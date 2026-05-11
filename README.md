# Spring Advanced 과제

## 📌 프로젝트 소개

Spring Boot 기반 일정 관리 API 프로젝트입니다.

이번 과제에서는 기존 프로젝트의 실행 오류를 해결하고, 인증 처리, 코드 리팩토링, Validation 분리, N+1 문제 개선, 테스트 코드 수정 등을 진행했습니다.

---

## 🛠️ 기술 스택

- Java 17
- Spring Boot 3.3.3
- Spring Data JPA
- MySQL
- Gradle
- JUnit 5
- Mockito
- JWT
- BCrypt

---

## ✅ 구현 사항

## Lv0. 프로젝트 실행 오류 해결

프로젝트 실행 시 `jwt.secret.key` 설정이 없어 애플리케이션이 실행되지 않는 문제가 있었습니다.

```text
Could not resolve placeholder 'jwt.secret.key'
```

이를 해결하기 위해 `application.properties`를 생성하고 JWT Secret Key를 추가했습니다.

```properties
jwt.secret.key=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=
```

이후 DB 설정이 없어 DataSource 오류가 발생했습니다.

```text
Failed to configure a DataSource
```

MySQL 데이터베이스를 생성하고 DB 연결 설정을 추가하여 서버가 정상 실행되도록 수정했습니다.

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/spring_advanced
spring.datasource.username=root
spring.datasource.password=내 MySQL 비밀번호
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

---

## Lv1. ArgumentResolver 등록

`AuthUserArgumentResolver`가 존재했지만 Spring MVC에 등록되어 있지 않아 동작하지 않는 문제가 있었습니다.

`WebConfig`를 생성하고 `WebMvcConfigurer`를 구현하여 Resolver를 등록했습니다.

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthUserArgumentResolver());
    }
}
```

이를 통해 Controller에서 `@Auth AuthUser`를 사용할 수 있도록 수정했습니다.

---

## Lv2. 코드 개선

### Lv2-1. Early Return 적용

회원가입 시 이메일 중복 검사를 비밀번호 암호화보다 먼저 수행하도록 순서를 변경했습니다.

기존에는 이미 존재하는 이메일이어도 비밀번호 암호화가 먼저 실행되는 불필요한 흐름이 있었습니다.

```java
if (userRepository.existsByEmail(signupRequest.getEmail())) {
    throw new InvalidRequestException("이미 존재하는 이메일입니다.");
}

String encodedPassword = passwordEncoder.encode(signupRequest.getPassword());
```

---

### Lv2-2. 불필요한 if-else 제거

`WeatherClient`의 중첩된 `if-else` 구조를 제거했습니다.

예외 상황을 먼저 처리하고, 정상 흐름은 아래로 자연스럽게 이어지도록 수정했습니다.

```java
if (!HttpStatus.OK.equals(responseEntity.getStatusCode())) {
    throw new ServerException("날씨 데이터를 가져오는데 실패했습니다.");
}

if (weatherArray == null || weatherArray.length == 0) {
    throw new ServerException("날씨 데이터가 없습니다.");
}
```

---

### Lv2-3. Validation을 DTO로 이동

비밀번호 검증 로직이 `UserService` 안에 있던 것을 DTO로 이동했습니다.

```java
@NotBlank
@Size(min = 8, message = "새 비밀번호는 8자 이상이어야 합니다.")
@Pattern(
        regexp = "^(?=.*\\d)(?=.*[A-Z]).+$",
        message = "새 비밀번호는 숫자와 대문자를 포함해야 합니다."
)
private String newPassword;
```

Controller에는 `@Valid`를 추가했습니다.

```java
@Valid @RequestBody UserChangePasswordRequest userChangePasswordRequest
```

이를 통해 요청값 검증 책임을 DTO로 분리했습니다.

---

## Lv3. N+1 문제 개선

Todo 목록 조회 시 연관된 User 정보를 함께 조회해야 했습니다.

기존 JPQL fetch join 방식 대신 `@EntityGraph`를 사용하도록 변경했습니다.

```java
@EntityGraph(attributePaths = {"user"})
Page<Todo> findAllByOrderByModifiedAtDesc(Pageable pageable);
```

이를 통해 Todo 조회 시 User를 함께 조회하여 N+1 문제를 방지했습니다.

---

## Lv4. 테스트 코드 수정 및 서비스 로직 개선

### Lv4-1. PasswordEncoderTest 수정

`matches()` 메서드의 인자 순서가 잘못되어 있어 수정했습니다.

```java
boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);
```

---

### Lv4-2. ManagerServiceTest 수정

Todo가 없는 상황에서 발생하는 예외 타입과 메시지에 맞게 테스트를 수정했습니다.

```java
InvalidRequestException exception = assertThrows(
        InvalidRequestException.class,
        () -> managerService.getManagers(todoId)
);

assertEquals("Todo not found", exception.getMessage());
```

---

### Lv4-3. CommentServiceTest 수정

Todo를 찾지 못하는 상황에서 기대하는 예외 타입을 `ServerException`에서 `InvalidRequestException`으로 수정했습니다.

```java
InvalidRequestException exception = assertThrows(InvalidRequestException.class, () -> {
    commentService.saveComment(authUser, todoId, request);
});
```

---

### Lv4-4. ManagerService null user 방어 로직 추가

Todo의 user가 null인 경우 NPE가 발생할 수 있어 null 체크를 추가했습니다.

```java
if (todo.getUser() == null || !ObjectUtils.nullSafeEquals(user.getId(), todo.getUser().getId())) {
    throw new InvalidRequestException("일정을 생성한 유저만 담당자를 지정할 수 있습니다.");
}
```

---

## 🧪 테스트

전체 테스트를 실행하여 모든 테스트가 통과하는 것을 확인했습니다.

```bash
./gradlew test
```

결과:

```text
BUILD SUCCESSFUL
```

---

## 🗂️ 커밋 내역

```text
lv1: fix auth user argument resolver
lv2-1: apply early return in signup
lv2-2: remove unnecessary else in weather client
lv2-3: move password validation to request dto
lv3: replace fetch join with entity graph
lv4-1: fix password encoder test
lv4-2: fix manager exception test name and message
lv4-3: fix comment exception test
lv4-4: handle null todo user in manager service
```

---

## 🚀 실행 방법

### 1. 프로젝트 clone

```bash
git clone <repository-url>
cd spring-advanced
```

### 2. MySQL 데이터베이스 생성

```sql
CREATE DATABASE spring_advanced;
```

### 3. application.properties 생성

`src/main/resources/application.properties` 파일을 생성하고 아래 내용을 추가합니다.

```properties
jwt.secret.key=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=

spring.datasource.url=jdbc:mysql://localhost:3306/spring_advanced
spring.datasource.username=root
spring.datasource.password=내 MySQL 비밀번호
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

### 4. 서버 실행

```bash
./gradlew bootRun
```

---

## 📌 마무리

이번 과제를 통해 Spring Boot 프로젝트의 실행 오류를 분석하고 해결하는 방법을 익혔습니다.

또한 ArgumentResolver 등록, Validation 분리, EntityGraph를 이용한 N+1 문제 개선, 테스트 코드 수정 등을 통해 기존 코드를 더 안정적이고 읽기 좋게 개선했습니다.