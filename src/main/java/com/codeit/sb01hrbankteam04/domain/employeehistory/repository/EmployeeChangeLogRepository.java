package com.codeit.sb01hrbankteam04.domain.employeehistory.repository;

import com.codeit.sb01hrbankteam04.domain.employee.entity.Employee;
import com.codeit.sb01hrbankteam04.domain.employeehistory.dto.request.ChangeLogRequest;
import com.codeit.sb01hrbankteam04.domain.employeehistory.dto.request.CountFilterRequest;
import com.codeit.sb01hrbankteam04.domain.employeehistory.dto.request.FilterRequest;
import com.codeit.sb01hrbankteam04.domain.employeehistory.dto.response.ChangeLogDto;
import com.codeit.sb01hrbankteam04.domain.employeehistory.dto.response.CursorPageResponseEmployeeDto;
import com.codeit.sb01hrbankteam04.domain.employeehistory.dto.response.DiffDto;
import com.codeit.sb01hrbankteam04.domain.employeehistory.type.ModifyType;
import com.codeit.sb01hrbankteam04.global.exception.CustomException;
import com.codeit.sb01hrbankteam04.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.hibernate.envers.query.criteria.AuditProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
@Transactional
public class EmployeeChangeLogRepository {

  private final EntityManager entityManager;

  /**
   * 직원(Employee) 변경 이력을 조회하여 커서 기반 페이지네이션을 적용한 응답을 반환
   */
  public CursorPageResponseEmployeeDto findChangeLogs(ChangeLogRequest request) {
    AuditReader auditReader = AuditReaderFactory.get(entityManager);
    AuditQuery query = auditReader.createQuery()
        .forRevisionsOfEntity(Employee.class, false,
            true) // 엔티티 + 리비전 정보 + 삭제된 엔티티도 포함해서 조회하기 위한 설정
        .setMaxResults(request.pageSize());

    FilterRequest filterRequest = new FilterRequest(request.employeeNumber(), request.type(),
        request.memo(), request.ipAddress(), request.atFrom(), request.atTo());

    applyFilters(query, filterRequest);

    if (request.lastRevisionId() != null) {
      query.add(AuditEntity.revisionNumber().lt(request.lastRevisionId()));
    }

    applySorting(query, request.sortField(), request.sortDirection());

    List<ChangeLogDto> logs = query.getResultList().stream()
        .filter(result -> result instanceof Object[])
        .map(result -> ChangeLogDto.convertToDto((Object[]) result))
        .toList();

//    CountFilterRequest countFilterRequest = new CountFilterRequest(request.atFrom(),
//        request.atTo());
//
//    Long totalElements = countChangeLogs(countFilterRequest);

//    Long nextIdAfter = logs.isEmpty() ? null : logs.get(logs.size() - 1).getId();

    //    String nextCursor =
//        nextIdAfter != null ? Base64.getEncoder().encodeToString(nextIdAfter.toString().getBytes())
//            : null;

//    String nextCursor = logs.isEmpty() ? null : String.valueOf(logs.get(logs.size() - 1).getAt());

    Long totalElements = countAllChangeLogs();

    boolean hasNext = logs.size() == request.pageSize();
    Long nextIdAfter = hasNext ? logs.get(logs.size() - 1).getId() : null;

    String nextCursor;

    if ("at".equals(request.sortField())) {
      nextCursor = hasNext ? String.valueOf(logs.get(logs.size() - 1).getAt()) : null;
    } else {
      nextCursor = hasNext ? String.valueOf(nextIdAfter) : null;
    }

    return CursorPageResponseEmployeeDto.builder()
        .content(logs)
        .nextCursor(nextCursor)
        .nextIdAfter(nextIdAfter)
        .size(request.pageSize())
        .totalElements(totalElements)
        .hasNext(hasNext)
        .build();
  }

  /**
   * @return 조건 없이 직원 수정 이력의 총 개수를 반환하는 메서드
   */
  private Long countAllChangeLogs() {
    AuditReader auditReader = AuditReaderFactory.get(entityManager);

    return (Long) auditReader.createQuery()
        .forRevisionsOfEntity(Employee.class, false, true)
        .addProjection(AuditEntity.revisionNumber().count())
        .getSingleResult();
  }

  /**
   * @param request fromDate, toDate
   * @return AuditReader를 사용해서 동적인 쿼리 작성 수정 이력의 전체 개수를 반환합니다
   */
  public Long countChangeLogs(CountFilterRequest request) {
    AuditReader auditReader = AuditReaderFactory.get(entityManager);

    AuditQuery query = auditReader.createQuery()
        .forRevisionsOfEntity(Employee.class, false, true)
        .addProjection(AuditEntity.revisionNumber().count());

    applyDateFilters(query, request); // 새 필터 메서드 사용

    return (Long) query.getSingleResult();
  }


  /**
   * 주어진 요청(request)에 따라 리비전 날짜 필터 적용 - fromDate 이후의 리비전만 조회 - toDate 이전의 리비전만 조회
   *
   * @param query   조회할 AuditQuery
   * @param request 날짜 필터 조건이 담긴 요청 객체
   */
  private void applyDateFilters(AuditQuery query, CountFilterRequest request) {
    if (request.fromDate() != null) {
      query.add(AuditEntity.revisionProperty("at").ge(request.fromDate()));
    }
    if (request.toDate() != null) {
      query.add(AuditEntity.revisionProperty("at").le(request.toDate()));
    }
  }

  /**
   * 주어진 요청(request)에 따라 AuditQuery에 필터 조건을 적용 - 직원 번호(employeeNumber) 검색 (대소문자 무시) - 변경 유형(revtype)
   * 필터링 - 메모(memo) 검색 (대소문자 무시) - IP 주소(ipAddress) 검색 - 특정 날짜 범위(atFrom ~ atTo) 내의 리비전만 조회
   *
   * @param query   조회할 AuditQuery
   * @param request 필터 조건이 담긴 요청 객체
   */
  private void applyFilters(AuditQuery query, FilterRequest request) {
    if (StringUtils.hasText(request.employeeNumber())) {
      query.add(
          AuditEntity.property("code").ilike("%" + request.employeeNumber().toLowerCase() + "%"));
    }

    if (request.type() != null) {
      RevisionType revisionType = ModifyType.toRevisionType(request.type());
      query.add(AuditEntity.property("revtype").eq(revisionType));
    }

    if (StringUtils.hasText(request.memo())) {
      query.add(
          AuditEntity.revisionProperty("memo").ilike("%" + request.memo().toLowerCase() + "%"));
    }

    if (StringUtils.hasText(request.ipAddress())) {
      query.add(AuditEntity.revisionProperty("ipAddress").like("%" + request.ipAddress() + "%"));
    }

    if (request.atFrom() != null) {
      query.add(AuditEntity.revisionProperty("at").ge(request.atFrom()));
    }

    if (request.atTo() != null) {
      query.add(AuditEntity.revisionProperty("at").le(request.atTo()));
    }
  }

  /**
   * AuditQuery에 동적으로 정렬 조건을 추가 - 정렬 가능한 필드: IP 주소(ipAddress), 변경 시간(at) - 정렬 방향: 오름차순(asc) 또는
   * 내림차순(desc)
   *
   * @param query         조회할 AuditQuery
   * @param sortField     정렬할 필드명
   * @param sortDirection 정렬 방향 (asc 혹은 desc)
   */
  private void applySorting(AuditQuery query, String sortField, String sortDirection) {
    boolean ascending = "asc".equalsIgnoreCase(sortDirection);

    Map<String, PropertyNameGetter> sortFieldMappings = Map.of(
        "ipAddress", AuditEntity::revisionProperty,
        "at", AuditEntity::revisionProperty
    );

    PropertyNameGetter propertyNameGetter = sortFieldMappings.getOrDefault(sortField,
        AuditEntity::revisionProperty);
    query.addOrder(ascending ? propertyNameGetter.get(sortField).asc()
        : propertyNameGetter.get(sortField).desc());
  }

  /**
   * AuditEntity의 속성명을 동적으로 가져오기 위한 함수형 인터페이스
   */
  @FunctionalInterface
  private interface PropertyNameGetter {

    AuditProperty get(String fieldName);

  }

  /**
   * 특정 리비전(revisionId)에 대한 직원(Employee) 변경 이력을 조회 - 리비전에서 변경된 employeeId 및 변경 유형(revtype) 가져오기 -
   * 삭제(DELETE): 삭제된 엔티티 조회 - 수정(UPDATE): 이전 리비전과 비교하여 변경된 필드만 반환 - 추가(ADD): 해당 리비전 엔티티 데이터 반환
   *
   * @param revisionId 조회할 리비전 ID
   * @return 변경 내역을 담은 DiffDto 리스트
   */
  public List<DiffDto> getRevisionDetails(Long revisionId) {
    AuditReader auditReader = AuditReaderFactory.get(entityManager);

    // 🔥 1. 현재 revisionId에서 employeeId 가져오기
    Object[] result;
    try {
      result = (Object[]) auditReader.createQuery()
          .forRevisionsOfEntity(Employee.class, false, true)
          .add(AuditEntity.revisionNumber().eq(revisionId))
          .addProjection(AuditEntity.id())
          .addProjection(AuditEntity.revisionType())
          .getSingleResult();
    } catch (NoResultException e) {
      throw new CustomException(ErrorCode.REVISION_FOUND_ERROR);
    }

    if (result == null || result.length < 2) {
      return List.of();
    }

    Long employeeId = ((Number) result[0]).longValue();
    RevisionType revtypeEnum = (RevisionType) result[1];
    int revtype = revtypeEnum.getRepresentation();

    Employee current;

    if (revtype == 2) {  //  삭제시
      List<Object[]> deletedEmployees = auditReader.createQuery()
          .forRevisionsOfEntity(Employee.class, false, true)
          .add(AuditEntity.id().eq(employeeId))
          .add(AuditEntity.revisionNumber().eq(revisionId))
          .getResultList();

      if (deletedEmployees.isEmpty()) {
        return List.of();
      }

      current = (Employee) deletedEmployees.get(0)[0];

    } else {
      current = auditReader.find(Employee.class, employeeId, revisionId);
    }

    if (current == null) {
      return List.of();
    }

    if (revtype == 1) {  // 수정(UPDATE)인 경우
      Number previousRevisionId = (Number) auditReader.createQuery()
          .forRevisionsOfEntity(Employee.class, false, true)
          .add(AuditEntity.id().eq(employeeId))
          .add(AuditEntity.revisionNumber().lt(revisionId))
          .addProjection(AuditEntity.revisionNumber().max())
          .getSingleResult();

      if (previousRevisionId != null) {
        Employee previous = auditReader.find(Employee.class, employeeId,
            previousRevisionId.longValue());

        if (previous == null) {
          return List.of();
        }

        return compareChanges(previous, current);
      }
    }

    return mapToDiffDto(current, revtype);
  }

  /**
   * 두 개의 Employee 엔티티를 비교하여 변경된 필드만 추출 변경되는 속성 (name, code, email, hireDate, position, department,
   * stats) 변경 사항이 있는 경우 DiffDto에 추가
   *
   * @param previous 이전 리비전의 Employee 데이터
   * @param current  현재 리비전의 Employee 데이터
   * @return 변경된 필드 목록을 담은 DiffDto 리스트
   */
  private List<DiffDto> compareChanges(Employee previous, Employee current) {
    List<DiffDto> changes = new ArrayList<>();

    if (!Objects.equals(previous.getName(), current.getName())) {
      changes.add(new DiffDto("name", previous.getName(), current.getName()));
    }
    if (!Objects.equals(previous.getCode(), current.getCode())) {
      changes.add(new DiffDto("employeeNumber", previous.getCode(), current.getCode()));
    }
    if (!Objects.equals(previous.getEmail(), current.getEmail())) {
      changes.add(new DiffDto("email", previous.getEmail(), current.getEmail()));
    }
    if (!Objects.equals(previous.getJoinedAt(), current.getJoinedAt())) {
      changes.add(new DiffDto("hireDate", convertInstantToLocalDate(previous.getJoinedAt()),
          convertInstantToLocalDate(current.getJoinedAt())));
    }
    if (!Objects.equals(previous.getPosition(), current.getPosition())) {
      changes.add(new DiffDto("position", previous.getPosition(), current.getPosition()));
    }
    if (previous.getDepartment() == null && current.getDepartment() == null) {
    } else if (previous.getDepartment() == null || current.getDepartment() == null) {
      changes.add(new DiffDto("department",
          previous.getDepartment() != null ? previous.getDepartment().getName() : "N/A",
          current.getDepartment() != null ? current.getDepartment().getName() : "N/A"));
    } else if (!Objects.equals(previous.getDepartment().getId(), current.getDepartment().getId())) {
      changes.add(new DiffDto("department",
          previous.getDepartment().getName(),
          current.getDepartment().getName()));
    }
    if (!Objects.equals(previous.getStatus(), current.getStatus())) {
      changes.add(
          new DiffDto("status", previous.getStatus().toString(), current.getStatus().toString()));
    }

    return changes;
  }

  /**
   * 리비전 유형(revtype)에 따라 Employee 데이터를 DiffDto로 변환
   * <p>
   * - 삭제(DELETE)된 경우: 기존 값을 유지하고 새로운 값은 null - 추가(ADD)된 경우: 기존 값은 null, 새로운 값만 포함
   *
   * @param employee 변경된 직원 정보
   * @param revtype  리비전 유형 (0: ADD, 1: MOD, 2: DEL)
   * @return 변경 내역을 담은 DiffDto 리스트
   */
  private List<DiffDto> mapToDiffDto(Employee employee, int revtype) {
    List<DiffDto> diffs = new ArrayList<>();

    boolean isDelete = (revtype == 2);  // 삭제(DELETE)면 true

    diffs.add(new DiffDto("name", isDelete ? employee.getName() : null,
        isDelete ? null : employee.getName()));
    diffs.add(new DiffDto("employeeNumber", isDelete ? employee.getCode() : null,
        isDelete ? null : employee.getCode()));
    diffs.add(new DiffDto("email", isDelete ? employee.getEmail() : null,
        isDelete ? null : employee.getEmail()));
    diffs.add(
        new DiffDto("hireDate", isDelete ? convertInstantToLocalDate(employee.getJoinedAt()) : null,
            isDelete ? null : convertInstantToLocalDate(employee.getJoinedAt())));
    diffs.add(new DiffDto("position", isDelete ? employee.getPosition() : null,
        isDelete ? null : employee.getPosition()));
    diffs.add(new DiffDto("department", isDelete ? employee.getDepartment().getName() : null,
        isDelete ? null : employee.getDepartment().getName()));
    diffs.add(new DiffDto("status", isDelete ? employee.getStatus().toString() : null,
        isDelete ? null : employee.getStatus().toString()));

    return diffs;
  }

  private String convertInstantToLocalDate(Instant instant) {
    return instant.atZone(ZoneId.of("UTC")).toLocalDate().toString();
  }
}