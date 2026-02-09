-- ================================================================
-- RevWorkforce HRM - Final Database Schema
-- 12 Tables | MySQL | All features covered
-- ================================================================

CREATE DATABASE IF NOT EXISTS rev_workforce;
USE rev_workforce;

-- ================================================================
-- 1. department
-- ================================================================
CREATE TABLE department (
                            department_id INT AUTO_INCREMENT PRIMARY KEY,
                            department_name VARCHAR(100) NOT NULL UNIQUE,
                            description TEXT,
                            is_active BOOLEAN DEFAULT TRUE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ================================================================
-- 2. designation
-- ================================================================
CREATE TABLE designation (
                             designation_id INT AUTO_INCREMENT PRIMARY KEY,
                             designation_name VARCHAR(100) NOT NULL UNIQUE,
                             description TEXT,
                             is_active BOOLEAN DEFAULT TRUE,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ================================================================
-- 3. employee
-- Role as ENUM (ADMIN > MANAGER > EMPLOYEE hierarchy in app layer)
-- Self-referencing manager_id for reporting structure
-- ================================================================
CREATE TABLE employee (
                          employee_id INT AUTO_INCREMENT PRIMARY KEY,
                          employee_code VARCHAR(20) NOT NULL UNIQUE,
                          first_name VARCHAR(100) NOT NULL,
                          last_name VARCHAR(100) NOT NULL,
                          email VARCHAR(255) NOT NULL UNIQUE,
                          password_hash VARCHAR(255) NOT NULL,
                          phone VARCHAR(20),
                          date_of_birth DATE,
                          gender ENUM('MALE', 'FEMALE', 'OTHER') DEFAULT NULL,
                          address TEXT,
                          emergency_contact_name VARCHAR(100),
                          emergency_contact_phone VARCHAR(20),
                          department_id INT,
                          designation_id INT,
                          joining_date DATE NOT NULL,
                          salary DECIMAL(12, 2),
                          manager_id INT,
                          role ENUM('EMPLOYEE', 'MANAGER', 'ADMIN') DEFAULT 'EMPLOYEE',
                          is_active BOOLEAN DEFAULT TRUE,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                          FOREIGN KEY (department_id) REFERENCES department(department_id) ON DELETE SET NULL,
                          FOREIGN KEY (designation_id) REFERENCES designation(designation_id) ON DELETE SET NULL,
                          FOREIGN KEY (manager_id) REFERENCES employee(employee_id) ON DELETE SET NULL,

                          INDEX idx_emp_email (email),
                          INDEX idx_emp_name (first_name, last_name),
                          INDEX idx_emp_dept (department_id),
                          INDEX idx_emp_manager (manager_id),
                          INDEX idx_emp_role (role)
);

-- ================================================================
-- 4. leave_type
-- ================================================================
CREATE TABLE leave_type (
                            leave_type_id INT AUTO_INCREMENT PRIMARY KEY,
                            leave_type_name VARCHAR(50) NOT NULL UNIQUE,
                            description TEXT,
                            default_days INT DEFAULT 0,
                            is_active BOOLEAN DEFAULT TRUE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ================================================================
-- 5. leave_balance
-- Per employee, per leave type, per year
-- available_balance auto-computed via generated column
-- ================================================================
CREATE TABLE leave_balance (
                               balance_id INT AUTO_INCREMENT PRIMARY KEY,
                               employee_id INT NOT NULL,
                               leave_type_id INT NOT NULL,
                               year INT NOT NULL,
                               total_leaves INT DEFAULT 0,
                               used_leaves INT DEFAULT 0,
                               available_balance INT GENERATED ALWAYS AS (total_leaves - used_leaves) STORED,
                               adjustment_reason VARCHAR(500),
                               adjusted_by INT,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                               FOREIGN KEY (employee_id) REFERENCES employee(employee_id) ON DELETE CASCADE,
                               FOREIGN KEY (leave_type_id) REFERENCES leave_type(leave_type_id) ON DELETE CASCADE,
                               FOREIGN KEY (adjusted_by) REFERENCES employee(employee_id) ON DELETE SET NULL,

                               UNIQUE KEY uk_emp_leave_year (employee_id, leave_type_id, year),
                               INDEX idx_balance_year (year)
);

-- ================================================================
-- 6. leave_application
-- Workflow: PENDING -> APPROVED / REJECTED / CANCELLED
-- ================================================================
CREATE TABLE leave_application (
                                   leave_id INT AUTO_INCREMENT PRIMARY KEY,
                                   employee_id INT NOT NULL,
                                   leave_type_id INT NOT NULL,
                                   start_date DATE NOT NULL,
                                   end_date DATE NOT NULL,
                                   total_days INT NOT NULL,
                                   reason TEXT NOT NULL,
                                   status ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') DEFAULT 'PENDING',
                                   manager_comments TEXT,
                                   actioned_by INT,
                                   applied_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   action_date TIMESTAMP NULL,
                                   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                   FOREIGN KEY (employee_id) REFERENCES employee(employee_id) ON DELETE CASCADE,
                                   FOREIGN KEY (leave_type_id) REFERENCES leave_type(leave_type_id),
                                   FOREIGN KEY (actioned_by) REFERENCES employee(employee_id) ON DELETE SET NULL,

                                   INDEX idx_leave_emp (employee_id),
                                   INDEX idx_leave_status (status),
                                   INDEX idx_leave_dates (start_date, end_date)
);

-- ================================================================
-- 7. holiday
-- Company holiday calendar managed by Admin
-- ================================================================
CREATE TABLE holiday (
                         holiday_id INT AUTO_INCREMENT PRIMARY KEY,
                         holiday_name VARCHAR(200) NOT NULL,
                         holiday_date DATE NOT NULL UNIQUE,
                         description VARCHAR(500),
                         year INT NOT NULL,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                         INDEX idx_holiday_year (year)
);

-- ================================================================
-- 8. performance_review
-- Workflow: DRAFT -> SUBMITTED -> REVIEWED
-- Employee fills self-assessment, Manager gives rating + feedback
-- ================================================================
CREATE TABLE performance_review (
                                    review_id INT AUTO_INCREMENT PRIMARY KEY,
                                    employee_id INT NOT NULL,
                                    reviewer_id INT,
                                    review_period VARCHAR(50) NOT NULL,
                                    key_deliverables TEXT,
                                    accomplishments TEXT,
                                    areas_of_improvement TEXT,
                                    self_assessment_rating INT CHECK (self_assessment_rating BETWEEN 1 AND 5),
                                    manager_rating INT CHECK (manager_rating BETWEEN 1 AND 5),
                                    manager_feedback TEXT,
                                    status ENUM('DRAFT', 'SUBMITTED', 'REVIEWED') DEFAULT 'DRAFT',
                                    submitted_date TIMESTAMP NULL,
                                    reviewed_date TIMESTAMP NULL,
                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                    FOREIGN KEY (employee_id) REFERENCES employee(employee_id) ON DELETE CASCADE,
                                    FOREIGN KEY (reviewer_id) REFERENCES employee(employee_id) ON DELETE SET NULL,

                                    INDEX idx_review_emp (employee_id),
                                    INDEX idx_review_status (status)
);

-- ================================================================
-- 9. goal
-- Employee goals with progress tracking + manager comments
-- ================================================================
CREATE TABLE goal (
                      goal_id INT AUTO_INCREMENT PRIMARY KEY,
                      employee_id INT NOT NULL,
                      title VARCHAR(200) NOT NULL,
                      description TEXT,
                      year INT NOT NULL,
                      deadline DATE NOT NULL,
                      priority ENUM('HIGH', 'MEDIUM', 'LOW') DEFAULT 'MEDIUM',
                      status ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED') DEFAULT 'NOT_STARTED',
                      progress INT DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
                      manager_comments TEXT,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                      FOREIGN KEY (employee_id) REFERENCES employee(employee_id) ON DELETE CASCADE,

                      INDEX idx_goal_emp (employee_id),
                      INDEX idx_goal_year (year)
);

-- ================================================================
-- 10. notification
-- In-app notifications for all events
-- reference_id + reference_type link to the related entity
-- ================================================================
CREATE TABLE notification (
                              notification_id INT AUTO_INCREMENT PRIMARY KEY,
                              recipient_id INT NOT NULL,
                              title VARCHAR(200) NOT NULL,
                              message TEXT NOT NULL,
                              type ENUM(
        'LEAVE_APPLIED', 'LEAVE_APPROVED', 'LEAVE_REJECTED', 'LEAVE_CANCELLED',
        'REVIEW_SUBMITTED', 'REVIEW_FEEDBACK',
        'GOAL_UPDATED', 'GOAL_COMMENT',
        'ANNOUNCEMENT', 'GENERAL'
    ) NOT NULL,
                              is_read BOOLEAN DEFAULT FALSE,
                              reference_id INT,
                              reference_type VARCHAR(50),
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                              FOREIGN KEY (recipient_id) REFERENCES employee(employee_id) ON DELETE CASCADE,

                              INDEX idx_notif_recipient (recipient_id),
                              INDEX idx_notif_read (is_read)
);

-- ================================================================
-- 11. announcement
-- Company-wide announcements by Admin
-- ================================================================
CREATE TABLE announcement (
                              announcement_id INT AUTO_INCREMENT PRIMARY KEY,
                              title VARCHAR(200) NOT NULL,
                              content TEXT NOT NULL,
                              created_by INT NOT NULL,
                              is_active BOOLEAN DEFAULT TRUE,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                              FOREIGN KEY (created_by) REFERENCES employee(employee_id),

                              INDEX idx_announce_active (is_active)
);

-- ================================================================
-- 12. activity_log
-- System audit trail for admin monitoring
-- ================================================================
CREATE TABLE activity_log (
                              log_id INT AUTO_INCREMENT PRIMARY KEY,
                              performed_by INT,
                              action VARCHAR(200) NOT NULL,
                              entity_type VARCHAR(50) NOT NULL,
                              entity_id INT,
                              details TEXT,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                              FOREIGN KEY (performed_by) REFERENCES employee(employee_id) ON DELETE SET NULL,

                              INDEX idx_log_entity (entity_type),
                              INDEX idx_log_created (created_at)
);

-- ================================================================
-- END OF SCHEMA
-- All seed data (departments, designations, leave types, employees,
-- holidays, announcements, etc.) will be handled via Spring Boot
-- APIs, models, and enums at the application layer.
-- ================================================================






