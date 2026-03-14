package org.example.workforce.repository;
import org.example.workforce.model.LeaveApplication;
import org.example.workforce.model.enums.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Integer>{
    Page<LeaveApplication> findByEmployee_EmployeeId(Integer employeeId, Pageable pageable);
    Page<LeaveApplication> findByEmployee_EmployeeIdAndStatus(Integer employeeId, LeaveStatus status, Pageable pageable);
    @Query("select la from LeaveApplication la where la.employee.manager.employeeCode = :managerCode")
    Page<LeaveApplication> findByManagerCode(@Param("managerCode") String managerCode, Pageable pageable);
    @Query("select la from LeaveApplication la where la.employee.manager.employeeCode = :managerCode AND la.status = :status")
    Page<LeaveApplication> findByManagerCodeAndStatus(@Param("managerCode") String managerCode, @Param("status") LeaveStatus status, Pageable pageable);
    @Query("select la from LeaveApplication la where la.employee.employeeId = :employeeId and la.status <> :cancelledStatus and la.status <> :rejectedStatus and la.startDate <= :endDate and la.endDate >= :startDate")
    List<LeaveApplication> findOverlappingLeaves(@Param("employeeId") Integer employeeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("cancelledStatus") LeaveStatus cancelledStatus, @Param("rejectedStatus") LeaveStatus rejectedStatus);
    long countByStatus(LeaveStatus status);
    @Query("SELECT la FROM LeaveApplication la WHERE la.status = :status AND la.startDate <= :today AND la.endDate >= :today")
    List<LeaveApplication> findActiveLeavesToday(@Param("status") LeaveStatus status, @Param("today") LocalDate today);
    @Query("SELECT la FROM LeaveApplication la WHERE la.employee.department.departmentId = :departmentId")
    Page<LeaveApplication> findByDepartmentId(@Param("departmentId") Integer departmentId, Pageable pageable);

    long countByEmployee_EmployeeIdAndStatus(Integer employeeId, LeaveStatus status);

    Page<LeaveApplication> findByStatus(LeaveStatus status, Pageable pageable);

    // List variant for AI analysis (no pagination needed)
    List<LeaveApplication> findByEmployeeEmployeeId(Integer employeeId);

    // Efficient team-on-leave count for AI analysis (replaces findAll + stream filter)
    @Query("SELECT COUNT(DISTINCT la.employee.employeeId) FROM LeaveApplication la " +
           "WHERE la.employee.manager.employeeId = :managerId AND la.status = :status " +
           "AND la.startDate <= :date AND la.endDate >= :date")
    int countTeamOnLeave(@Param("managerId") Integer managerId,
                         @Param("status") LeaveStatus status,
                         @Param("date") LocalDate date);

    @Query("SELECT la FROM LeaveApplication la WHERE la.employee.manager.employeeCode = :managerCode AND la.status = :status AND la.startDate <= :endDate AND la.endDate >= :startDate")
    List<LeaveApplication> findTeamLeavesBetween(@Param("managerCode") String managerCode, @Param("status") LeaveStatus status, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("select la from LeaveApplication la where la.employee.manager.employeeCode = :managerCode AND la.employee.role <> org.example.workforce.model.enums.Role.MANAGER")
    Page<LeaveApplication> findByManagerCodeExcludingManagers(@Param("managerCode") String managerCode, Pageable pageable);

    @Query("select la from LeaveApplication la where la.employee.manager.employeeCode = :managerCode AND la.status = :status AND la.employee.role <> org.example.workforce.model.enums.Role.MANAGER")
    Page<LeaveApplication> findByManagerCodeAndStatusExcludingManagers(@Param("managerCode") String managerCode, @Param("status") LeaveStatus status, Pageable pageable);
}
