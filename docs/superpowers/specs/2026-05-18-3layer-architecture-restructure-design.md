# Spring Boot 3-Layer Structure Restore Design

Date: 2026-05-18
Status: Revised on 2026-05-19
Scope: Restore Java Spring Boot package structure; keep behavior unchanged

## 1. Objective

Restore the Java backend to a conventional Spring Boot package structure using the layer names the project already used:

- `controller`
- `service`
- `repository`

Supporting packages remain top-level Spring Boot support packages:

- `dto`
- `entity`
- `mapper`
- `config`
- `security`
- `exception`
- `utils`

The prior proposal to rename the Java packages into `presentation`, `application`, and `dataaccess` is rejected for this project because it does not match the requested Spring Boot 3-layer convention.

## 2. Services

Java service:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
```

Python AI service:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
```

This revision focuses on the Java service. The Python service can be handled separately if its current package names also need to be simplified.

## 3. Java Target Structure

Target Java package root:

```text
src/main/java/com/historytalk/
```

Target structure:

```text
com/historytalk/
  HistoryTalkApplication.java
  config/
  controller/
    authentication/
    character/
    chat/
    historicalContext/
    quiz/
  dto/
    authentication/
    character/
    chat/
    exception/
    historicalContext/
    quiz/
    user/
  entity/
    character/
    chat/
    enums/
    historicalContext/
    quiz/
    user/
  exception/
  mapper/
    character/
    user/
  repository/
  security/
  service/
    authentication/
    character/
    chat/
    historicalContext/
    quiz/
  utils/
    authentication/
```

## 4. Package Mapping

Use these mappings when restoring from the rejected layered structure:

```text
presentation/<domain>/controller -> controller/<domain>
presentation/<domain>/dto        -> dto/<domain>
presentation/common/dto          -> dto or dto/exception

application/<domain>/service     -> service/<domain>
application/<domain>/mapper      -> mapper/<domain>

dataaccess/<domain>/entity       -> entity/<domain>
dataaccess/<domain>/repository   -> repository
dataaccess/shared/entity/enums   -> entity/enums

common/config                    -> config
common/exception                 -> exception
common/security                  -> security
common/util                      -> utils
common/integration/ai            -> service/chat
```

Special cases:

```text
common/security/JwtProperties.java -> utils/authentication/JwtProperties.java
common/security/JwtUtils.java      -> utils/authentication/JwtUtils.java
```

## 5. Naming Decisions

- Keep the normalized Java base package: `com.historytalk`.
- Restore the historical package segment `historicalContext` to match the existing project convention.
- Keep Java class names unchanged.
- Do not rename business types or endpoints as part of this structure restore.

## 6. Verification Gates

Run after the package restore:

```powershell
mvn -q -DskipTests compile
```

Working directory:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
```

Also verify no Java source imports still reference the rejected layered packages:

```powershell
rg "com\.historytalk\.(presentation|application|dataaccess|common)" Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java
```

Expected result: no matches.

## 7. Success Criteria

- Java source folders use `controller`, `service`, and `repository` as the main layers.
- Entities, DTOs, mappers, config, security, exceptions, and utilities use their conventional top-level packages.
- No Java package declarations or imports reference the rejected layered package names.
- Java compile succeeds.
- Existing API behavior remains unchanged.
