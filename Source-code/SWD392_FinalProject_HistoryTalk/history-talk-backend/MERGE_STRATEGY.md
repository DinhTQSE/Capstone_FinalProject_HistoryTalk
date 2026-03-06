# Team Merge Strategy - History Talk Backend

**Purpose**: Minimize merge conflicts and coordinate team development on shared infrastructure.

---

## 📁 File Ownership

### Shared Files (Require Coordination ⚠️)

| File | Owner | Purpose | Notes |
|------|-------|---------|-------|
| `SecurityConfig.java` | Backend Lead | API security routes | Add endpoint patterns here |
| `JwtAuthenticationFilter.java` | Backend Lead | JWT extraction logic | Only change if adding new auth method |
| `application.properties` | DevOps/Config Lead | Configuration | Use env variables, don't hardcode |
| `pom.xml` | Build Lead | Dependencies | Review before adding new libs |
| `GlobalExceptionHandler.java` | Backend Lead | Exception handling | Can be extended by modules |

### Module-Specific Files (Isolated 🟢)

```
src/main/java/
├── controller/HistoricalContextController.java      ← Only for Historical Context
├── service/HistoricalContextService.java            ← Only for Historical Context
├── repository/HistoricalContextRepository.java      ← Only for Historical Context
├── entity/HistoricalContext.java                    ← Only for Historical Context
├── dto/                                              ← Each module owns its DTOs
│   ├── CreateHistoricalContextRequest.java
│   ├── UpdateHistoricalContextRequest.java
│   └── HistoricalContextResponse.java
└── exception/                                        ← Shared, can be reused
    ├── ResourceNotFoundException.java
    ├── DuplicateResourceException.java
    └── ... (add more as needed)
```

---

## 🔄 Git Workflow

### Before Starting Work

```bash
# 1. Pull latest changes
git pull origin main

# 2. Check if shared files modified recently
git log --oneline -n 10 src/main/java/com/historyTalk/config/SecurityConfig.java

# 3. Reach out to team if you need to modify shared files
```

### Creating a Feature Branch

```bash
# Use clear naming: feature/module-name OR fix/issue-name
git checkout -b feature/character-management
git checkout -b fix/jwt-validation

# NOT: feature/my-changes OR bugfix/stuff
```

### Committing Code

```bash
# Clear commit messages help with merge review
git commit -m "feat(historical-context): Add bulk delete endpoint"
git commit -m "fix(security): Handle expired JWT tokens"
git commit -m "docs: Update API documentation"

# Use format: type(scope): description
```

### Pull Request Process

1. **Push to feature branch**:
   ```bash
   git push origin feature/character-management
   ```

2. **Create Pull Request with this checklist**:
   ```markdown
   ## Changes
   - [ ] Module-specific only (controller, service, entity)
   - [ ] No changes to ConfigSecurity.java
   - [ ] No changes to application.properties (only add new module config)
   - [ ] No new dependencies without approval
   
   ## Testing
   - [ ] mvn clean install passes
   - [ ] Endpoints tested in Swagger UI
   - [ ] No breaking changes to existing APIs
   
   ## Documentation
   - [ ] README updated if needed
   - [ ] Code comments added for complex logic
   ```

3. **Code Review & Merge**:
   - Minimum 1 approval required
   - Build must pass (mvn clean install)
   - Then: Squash & merge OR Rebase & merge

---

## 📋 Configuration Management

### Database Credentials

**❌ DO NOT COMMIT**:
```yaml
# ❌ Wrong - hardcoded passwords
spring.datasource.password: 123456
```

**✅ DO THIS** - Use environment variables:
```bash
# Terminal setup (macOS/Linux)
export DB_URL="jdbc:postgresql://localhost:5432/history_talk_db"
export DB_USER="postgres"
export DB_PASSWORD="your_password"

# Or create .env file (add to .gitignore)
echo "export DB_PASSWORD=123456" > .env
source .env
```

### JWT Secret

**Local Development**:
```bash
export JWT_SECRET="local_secret_key_at_least_32_chars"
```

**Production Setup** (handled by DevOps):
```bash
# Generate secure key
openssl rand -base64 32
# Then export to environment
export JWT_SECRET="<generated_key>"
```

---

## 🚨 Conflict Prevention

### Endpoint Conventions

Follow these patterns to avoid POST /historical-contexts conflicts:

```
/v1/historical-contexts/**          ← Historical Context Module
/v1/characters/**                   ← Character Module (future)
/v1/documents/**                    ← Document Module (future)
/api/v1/auth/**                     ← Authentication Module (future)
/api/v1/users/**                    ← User Management Module (future)
```

**Rule**: Always use `/v1/` prefix for your first endpoint!

### Dependency Management

Before adding new dependency:

```bash
# Check if already exists
mvn dependency:tree | grep "your-lib-name"

# If adding new library:
# 1. Get approval from Build Lead
# 2. Update pom.xml in separate commit
# 3. Test: mvn clean install
# 4. Document why it's needed
```

---

## 📊 Module Integration Guide

When adding a new module (e.g., Character, Document):

### Step 1: Create Module Structure
```
src/main/java/com/historyTalk/
├── entity/Character.java
├── repository/CharacterRepository.java
├── service/CharacterService.java
├── controller/CharacterController.java
└── dto/
    ├── CreateCharacterRequest.java
    └── CharacterResponse.java
```

### Step 2: Update Shared Files (Coordinate!)

**SecurityConfig.java** - Add endpoint pattern:
```java
.requestMatchers("/v1/characters/**").authenticated()
```

**application.properties** - No changes needed (uses env vars)

**pom.xml** - Only if new dependencies needed

### Step 3: Test Integration
```bash
mvn clean install
mvn spring-boot:run
# Test endpoints in Swagger UI
```

### Step 4: Create Pull Request
- Link to any related issues
- Add description of integration points
- Request review from backend lead

---

## 🟢 Ready to Merge Checklist

Before merging a Pull Request:

- [ ] **No conflicts** with main branch
- [ ] **Build passes**: `mvn clean install` succeeds
- [ ] **Tests pass**: All test suites run
- [ ] **No security issues**: No hardcoded credentials
- [ ] **No API breaking changes**: Existing endpoints work
- [ ] **Shared files approved**: If modified, code review done
- [ ] **Documentation updated**: README, comments added
- [ ] **Code follows style**: Consistent with existing code

---

## 📞 Questions?

**Who to contact**:
- **SecurityConfig changes**: Backend Lead
- **Database config**: DevOps Lead
- **Dependency conflicts**: Build Lead
- **Architecture questions**: Tech Lead

---

## 🔄 Last Updated
- **Date**: February 28, 2026
- **Reviewed by**: Backend Team
- **Next review**: March 15, 2026
