# Store Land — Backend

API REST do Store Land, desenvolvida com Java, Spring Boot, Spring Security e
PostgreSQL.

## Requisitos

- JDK 27 (configurado no `pom.xml`).
- PostgreSQL disponível localmente ou em uma instância acessível.
- SMTP configurado para enviar e-mails de recuperação de senha.

O projeto inclui Maven Wrapper; não é necessário instalar Maven globalmente.

## Configuração

A configuração principal está em
`src/main/resources/application.properties`. Por padrão, o backend usa:

- URL JDBC: `jdbc:postgresql://localhost:5432/store_land`
- Usuário do banco: `postgres`
- Aplicação: `http://localhost:8080`
- Frontend permitido pelo CORS: `http://localhost:3000`

Defina a senha do banco no ambiente com `SPRING_DATASOURCE_PASSWORD`. Não
armazene credenciais reais no repositório.

As variáveis abaixo podem substituir os valores padrão:

| Variável | Uso | Padrão |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | URL JDBC do PostgreSQL | `jdbc:postgresql://localhost:5432/store_land` |
| `SPRING_DATASOURCE_USERNAME` | Usuário do banco | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco | Sem valor documentado; configure no ambiente |
| `FRONTEND_URL` | Origem permitida pelo CORS e base dos links de recuperação | `http://localhost:3000` |
| `MAIL_HOST` | Host SMTP | `localhost` |
| `MAIL_PORT` | Porta SMTP | `1025` |
| `MAIL_USERNAME` | Usuário SMTP, quando necessário | Vazio |
| `MAIL_PASSWORD` | Senha SMTP, quando necessário | Vazio |
| `MAIL_SMTP_AUTH` | Ativa autenticação SMTP | `false` |
| `MAIL_SMTP_STARTTLS` | Ativa STARTTLS | `false` |
| `MAIL_FROM` | Endereço remetente | `no-reply@store-land.local` |
| `SESSION_COOKIE_SECURE` | Marca o cookie de sessão como `Secure` | `false` |
| `SWAGGER_UI_ENABLED` | Ativa/desativa Swagger UI e OpenAPI | `true` |

Em produção, configure credenciais por um gerenciador de segredos/variáveis de
ambiente, use HTTPS e defina `SESSION_COOKIE_SECURE=true`.

## Executar

No Windows, no diretório `backend/store_land`:

```powershell
.\mvnw.cmd spring-boot:run
```

No macOS/Linux:

```bash
./mvnw spring-boot:run
```

O servidor inicia em `http://localhost:8080`.

## Testes e empacotamento

Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

macOS/Linux:

```bash
./mvnw test
./mvnw package
```

## Swagger / OpenAPI

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Especificação OpenAPI: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Defina `SWAGGER_UI_ENABLED=false` para desativar a interface e a especificação.

## API de autenticação

Base URL local: `http://localhost:8080/api/auth`.

| Método | Caminho | Acesso | Descrição |
| --- | --- | --- | --- |
| `GET` | `/csrf` | Público | Emite token CSRF e cookie `XSRF-TOKEN`. |
| `POST` | `/register` | Público + CSRF | Cria uma conta e inicia sessão. |
| `POST` | `/login` | Público + CSRF | Autentica com usuário ou e-mail. |
| `GET` | `/me` | Sessão | Retorna os dados da conta autenticada. |
| `POST` | `/logout` | Sessão + CSRF | Encerra a sessão. |
| `POST` | `/forgot-password` | Público + CSRF | Solicita um e-mail de recuperação. |
| `POST` | `/reset-password` | Público + CSRF | Define uma nova senha com token válido. |

Exemplo de cadastro:

```json
{
  "fullName": "Usuário de Teste",
  "username": "usuario_teste",
  "email": "teste@example.com",
  "password": "SenhaTeste@123",
  "confirmPassword": "SenhaTeste@123"
}
```

Exemplo de login:

```json
{
  "username": "usuario_teste",
  "password": "SenhaTeste@123"
}
```

### CSRF, sessão e Insomnia

As rotas `POST` usam proteção CSRF. Antes de chamar uma delas:

1. Faça `GET http://localhost:8080/api/auth/csrf`.
2. Preserve o cookie `XSRF-TOKEN` recebido.
3. Envie o valor `token` do JSON no cabeçalho `X-XSRF-TOKEN`.
4. Envie `Content-Type: application/json` e mantenha o cookie jar habilitado.

Após login/cadastro, preserve também o cookie de sessão `JSESSIONID` para
chamadas protegidas como `GET /api/auth/me`. Uma resposta `403` normalmente
indica token CSRF ou cookies ausentes/incompatíveis; `401` indica credenciais
inválidas ou sessão ausente.

O frontend já implementa esse fluxo em
`../../frontend/store_land/src/services/authApi.js`.

### Recuperação de senha

Configure um servidor SMTP acessível antes de solicitar recuperação. O link
enviado expira em 30 minutos; o banco guarda somente o hash do token. A resposta
da solicitação é genérica para não revelar se o endereço de e-mail existe.

## Banco de dados

O schema de referência está em
`src/main/java/br/com/store_land/database/schema.sql`. A aplicação conecta ao
PostgreSQL usando Spring Data JPA para persistir e consultar os tokens de
redefinição de senha (`PasswordResetToken`). Outras operações de autenticação
continuam usando `JdbcTemplate`.

O `application.properties` atual define `spring.jpa.hibernate.ddl-auto=update`
e `spring.jpa.show-sql=true`. Revise essas opções antes de produção: prefira
migrações versionadas e evite registrar SQL ou dados sensíveis em logs.
