# Исправление ошибки JWT в тестах

**Исходная ошибка** в `AuthApiTest.registersUserAndReturnsJwt`:
`Failed to create a JWS Signer -> The secret length must be at least 256 bits`.

Причина: ключ HS256 короче 32 байт. Хотя во всех `application.yml` указан подходящий длинный секрет по умолчанию, переменная среды `JWT_SECRET` может его переопределить.

Исправления:

1. `AuthApiTest`, `BookingWorkflowTest` и `HotelServiceTest` теперь используют независимый тестовый ключ длиной не менее 32 байт, заданный в `@SpringBootTest(properties=...)`.
2. `JwtTokens` проверяет длину ключа при создании компонента и сообщает, что для HS256 нужно минимум 32 байта.
3. В `README.md` добавлена диагностика PowerShell и запуск с длинным ключом.

Повторная проверка в корне проекта:

```powershell
mvn clean test
mvn clean package
```

Самостоятельно запускать Maven в окружении подготовки архива не удалось (Maven не установлен), поэтому успешное прохождение тестов после исправления необходимо подтвердить локально.
