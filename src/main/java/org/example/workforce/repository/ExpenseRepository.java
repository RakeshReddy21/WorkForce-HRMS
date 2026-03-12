package org.example.workforce.repository;

import org.example.workforce.model.Expense;
import org.example.workforce.model.enums.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Integer> {

    // Employee: my expenses
    Page<Expense> findByEmployeeEmployeeIdAndStatusIn(Integer employeeId, List<ExpenseStatus> statuses, Pageable pageable);
    Page<Expense> findByEmployeeEmployeeId(Integer employeeId, Pageable pageable);

    // Manager: team expenses pending manager approval
    @Query("SELECT e FROM Expense e WHERE e.employee.manager.employeeId = :managerId AND e.status = :status")
    Page<Expense> findTeamExpensesByStatus(@Param("managerId") Integer managerId,
                                           @Param("status") ExpenseStatus status,
                                           Pageable pageable);

    // Finance: all expenses pending finance approval
    Page<Expense> findByStatus(ExpenseStatus status, Pageable pageable);

    // Admin: all expenses
    Page<Expense> findByStatusIn(List<ExpenseStatus> statuses, Pageable pageable);

    // Dashboard stats
    @Query("SELECT COUNT(e) FROM Expense e WHERE e.employee.employeeId = :empId AND e.status = :status")
    long countByEmployeeAndStatus(@Param("empId") Integer employeeId, @Param("status") ExpenseStatus status);

    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM Expense e WHERE e.employee.employeeId = :empId AND e.status = :status")
    BigDecimal sumAmountByEmployeeAndStatus(@Param("empId") Integer employeeId, @Param("status") ExpenseStatus status);

    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM Expense e WHERE e.employee.employeeId = :empId " +
           "AND e.status = 'REIMBURSED' AND e.expenseDate BETWEEN :start AND :end")
    BigDecimal sumReimbursedInPeriod(@Param("empId") Integer employeeId,
                                     @Param("start") LocalDate start,
                                     @Param("end") LocalDate end);

    // Count by status for dashboard
    long countByStatus(ExpenseStatus status);

    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM Expense e WHERE e.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") ExpenseStatus status);
}

