# RevWorkforce – Full Stack HRMS Portal

RevWorkforce is a full-stack Human Resource Management System (HRMS) web application designed to streamline workforce operations including employee management, attendance tracking, leave management, performance reviews, and real-time team communication. The backend is built using Java Spring Boot and MySQL, providing secure APIs, business logic, and platform services. The frontend is developed using Angular.

## Repositories

**Backend Repository:** [https://github.com/Conginzant-Java-FSE/RevWorkforce](https://github.com/Conginzant-Java-FSE/RevWorkforce)

**Frontend Repository:** [https://github.com/Conginzant-Java-FSE/RevWorkForce-Frontend](https://github.com/Conginzant-Java-FSE/RevWorkForce-Frontend)

## Key Features

### Admin Features
- Comprehensive admin dashboard with workforce analytics and insights
- Employee registration, onboarding, activation/deactivation, and manager assignment
- Department and designation CRUD with activate/deactivate support
- Company-wide leave management with leave type configuration and balance adjustments
- Organization-wide attendance monitoring with late tracking and reporting
- Performance review oversight across all departments
- Company-wide announcements for internal communication
- Activity logs providing a complete audit trail of admin actions
- IP access control to restrict system access by IP range
- Office location management for geo-fenced attendance

### Manager Features
- Manager dashboard with team-level analytics
- Team member overview and management
- Approve or reject team leave requests with leave calendar visibility
- Monitor team attendance records and patterns
- Conduct performance reviews, set goals, and provide feedback for direct reports
- Self-service attendance check-in/check-out and leave applications

### Employee Features
- Personalized employee dashboard with attendance and leave summaries
- Geo-fenced attendance check-in and check-out with IP validation
- Leave application submission with balance tracking
- Self-assessment and goal progress tracking for performance reviews
- Employee directory for searching and viewing colleagues
- View company-wide announcements
- Profile and password management via settings

### Platform Features
- Secure authentication with JWT access tokens, refresh tokens, and Two-Factor Authentication (2FA) via email OTP
- Role-based access control (Admin, Manager, Employee)
- Real-time one-on-one chat messaging via WebSocket (STOMP)
- Real-time push notifications for important events
- Geo-fenced attendance with configurable office radius and location validation
- Swagger/OpenAPI documentation for all REST endpoints
- Global exception handling with structured API responses
- Dockerized backend for containerized deployment
- Optimized RESTful APIs for seamless frontend-backend communication
- Scalable and secure Spring Boot backend architecture

## Tech Stack

| Layer        | Technologies                                      |
|--------------|---------------------------------------------------|
| Backend      | Java 17, Spring Boot 4, Spring Security, Spring Data JPA |
| Frontend     | Angular 21, TypeScript, Tailwind CSS 4            |
| Database     | MySQL 8                                           |
| Auth         | JWT (jjwt), 2FA with Email OTP                    |
| Real-Time    | Spring WebSocket, STOMP.js                        |
| API Docs     | Swagger / SpringDoc OpenAPI                       |
| Build Tools  | Maven, npm                                        |
| Testing      | JUnit 5, Mockito, H2 (Backend) · Vitest (Frontend) |
| DevOps       | Docker, Git                                       |
| Other        | Lombok, Spring Mail, JaCoCo (Code Coverage)       |

## Roles

| Role     | Access                                              |
|----------|-----------------------------------------------------|
| Admin    | Full system access — manage employees, departments, leaves, attendance, reviews, announcements, IP access, and activity logs |
| Manager  | Team management — approve leaves, monitor attendance, conduct performance reviews |
| Employee | Self-service portal — attendance, leaves, performance, directory, announcements |

## Architecture

RevWorkforce follows a full-stack architecture where the Angular frontend communicates with the Spring Boot backend through REST APIs and WebSocket connections. MySQL is used for managing structured data including employees, departments, attendance, leaves, performance reviews, chat messages, notifications, and audit logs. Authentication is handled via JWT with optional 2FA, and role-based guards on both frontend and backend ensure secure access control.

## ER Diagram

![ER Diagram](ER%20diagram%201.png)

## Project Structure

```
src/
├── main/java/org/example/workforce/
│   ├── config/         # Security, JWT, WebSocket, Swagger, IP Filter
│   ├── controller/     # REST & WebSocket controllers
│   ├── dto/            # Request/Response objects
│   ├── exception/      # Global exception handling
│   ├── model/          # JPA entities & enums
│   ├── repository/     # Spring Data repositories
│   ├── service/        # Business logic
│   └── util/           # Utility classes
└── test/java/          # Unit & integration tests
```

## Prerequisites

- Java 17+
- MySQL 8+
- Node.js 18+ (for frontend)

## Setup

### Backend

1. Clone the repository:

```bash
git clone https://github.com/Conginzant-Java-FSE/RevWorkforce.git
cd RevWorkforce
```

2. Create a MySQL database.

3. Update `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/your_db
spring.datasource.username=your_user
spring.datasource.password=your_password
jwt.secret=your_base64_secret
jwt.expiration=86400000
```

4. Run:

```bash
./mvnw spring-boot:run
```

Backend runs on `http://localhost:8080`

### Frontend

1. Clone the frontend repository:

```bash
git clone https://github.com/Conginzant-Java-FSE/RevWorkForce-Frontend.git
cd RevWorkForce-Frontend
```

2. Install dependencies and start:

```bash
npm install
npm start
```

Frontend runs on `http://localhost:4200`

## Running Tests

### Backend

```bash
./mvnw test
```

### Frontend

```bash
npm test
```

## Docker

Build and run the backend using Docker:

```bash
docker build -t revworkforce-backend .
docker run -p 8080:8080 revworkforce-backend
```

## API Docs

Swagger UI: `http://localhost:8080/swagger-ui.html`