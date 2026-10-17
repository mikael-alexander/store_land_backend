# Guia de Estudos — Store Land Backend

> Este guia documenta **todas as técnicas, padrões e anotações** utilizados no backend Store Land, explicando **o que** é, **como** funciona e **por que** foi escolhido.

---

## Índice

1. [Visão Geral do Projeto](#1-visão-geral-do-projeto)
2. [Estrutura de Pastas](#2-estrutura-de-pastas)
3. [Dependências Maven (pom.xml)](#3-dependências-maven-pomxml)
4. [Configurações Externas (application.properties)](#4-configurações-externas-applicationproperties)
5. [Classe Principal — SpringBootApplication](#5-classe-principal---springbootapplication)
6. [Configurações — Package `config`](#6-configurações---package-config)
   - 6.1 [CorsConfig — Cross-Origin Resource Sharing](#61-corsconfig---cross-origin-resource-sharing)
   - 6.2 [SecurityConfig — Spring Security](#62-securityconfig---spring-security)
   - 6.3 [OpenApiConfig — Documentação Swagger/OpenAPI](#63-openapiconfig---documentação-swaggeropenapi)
7. [Controllers — Package `controller`](#7-controllers---package-controller)
   - 7.1 [AuthController — Endpoints de Autenticação](#71-authcontroller---endpoints-de-autenticação)
   - 7.2 [ApiExceptionHandler — Tratamento Global de Erros](#72-apiexceptionhandler---tratamento-global-de-erros)
8. [DTOs — Package `dto`](#8-dtos---package-dto)
9. [Schema do Banco de Dados (schema.sql)](#9-schema-do-banco-de-dados-schemsql)
10. [Resumo de Todas as Anotações](#10-resumo-de-todas-as-anotações)
11. [Padrões e Boas Práticas Utilizados](#11-padrões-e-boas-práticas-utilizados)

---

## 1. Visão Geral do Projeto

O **Store Land** é um backend RESTful construído com **Spring Boot 4.1.1** e **Java 27**. Ele implementa um sistema completo de autenticação baseado em **sessão HTTP com cookies**, protegido contra ataques CSRF (Cross-Site Request Forgery).

### Tecnologias principais:

| Tecnologia | Finalidade |
|---|---|
| Spring Boot 4.1.1 | Framework principal (automação de configuração, embutido Tomcat) |
| Spring Security | Autenticação, autorização, CSRF, gerenciamento de sessão |
| Spring WebMVC | REST API (controllers, request/response) |
| Spring Data JPA (JdbcTemplate) | Acesso ao banco de dados via SQL direto |
| PostgreSQL / H2 | Banco de dados relacional (PostgreSQL em produção, H2 em desenvolvimento) |
| Spring Mail | Envio de e-mails (recuperação de senha) |
| SpringDoc OpenAPI | Documentação automática da API (Swagger UI) |
| BCrypt | Hashing de senhas |

### Por que sessão com cookies em vez de JWT?

O projeto escolheu **sessão HTTP com cookies** porque:
- **Segurança nativa contra XSS/CSRF**: Cookies com `HttpOnly` e `SameSite=Lax` previnem acesso via JavaScript.
- **Logout real**: Invalidar a sessão no servidor é imediato; JWT requer listas de revogação.
- **Simplicidade**: O navegador gerencia os cookies automaticamente; não há necessidade de armazenar tokens no frontend.
- **CSRF Token**: Proteção adicional contra ataques cross-site em requisições mutáveis (POST).

---

## 2. Estrutura de Pastas

```
src/main/java/br/com/store_land/
├── StoreLandApplication.java        # Ponto de entrada (main)
├── config/
│   ├── CorsConfig.java              # Configuração CORS
│   ├── OpenApiConfig.java           # Configuração Swagger/OpenAPI
│   └── SecurityConfig.java          # Configuração Spring Security
├── controller/
│   ├── ApiExceptionHandler.java     # Tratamento global de exceções
│   └── AuthController.java          # Endpoints de autenticação
├── dto/
│   ├── AuthResponse.java            # Resposta de autenticação (record)
│   ├── ForgotPasswordRequest.java   # Requisição de esqueci a senha (record)
│   ├── LoginRequest.java            # Requisição de login (record)
│   ├── MessageResponse.java         # Resposta genérica com mensagem (record)
│   ├── RegisterRequest.java         # Requisição de cadastro (record)
│   ├── ResetPasswordRequest.java    # Requisição de redefinição (record)
│   └── UserDto.java                 # Dados do usuário (record)
└── database/
    └── schema.sql                   # DDL do banco de dados
```

### Por que essa estrutura?

- **Separação por responsabilidade**: `config/` para configurações, `controller/` para endpoints, `dto/` para transferência de dados.
- **Sem package `model` ou `repository`**: O projeto usa `JdbcTemplate` diretamente nos controllers/configs, sem camada de entidades JPA. Isso é uma escolha deliberada para manter simplicidade — o projeto é pequeno e não precisa do overhead do ORM.

---

## 3. Dependências Maven (pom.xml)

### 3.1. `spring-boot-starter-parent`

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
</parent>
```

**O que é:** O "pai" do projeto no Maven que centraliza as versões de todas as dependências do ecossistema Spring Boot.

**Por que usar:** Elimina a necessidade de declarar versões manualmente para cada dependência Spring. Garante compatibilidade entre os módulos Spring (Security, Web, Mail, etc.).

### 3.2. `spring-boot-starter-webmvc`

```xml
<artifactId>spring-boot-starter-webmvc</artifactId>
```

**O que é:** Starter que inclui o Spring WebMVC e o Tomcat embutido.

**Por que usar:** Permite criar endpoints REST com `@RestController`, `@GetMapping`, `@PostMapping`, etc. O Tomcat embutido faz o aplicativo rodar sem precisar de um servidor externo.

> **Nota:** No Spring Boot 4.x, `spring-boot-starter-web` foi renomeado para `spring-boot-starter-webmvc`.

### 3.3. `spring-boot-starter-security`

```xml
<artifactId>spring-boot-starter-security</artifactId>
```

**O que é:** Starter que adiciona Spring Security ao projeto.

**Por que usar:** Fornece autenticação, autorização, proteção CSRF, gerenciamento de sessão, encoding de senhas e muito mais de forma declarativa.

### 3.4. `spring-boot-starter-data-jpa`

```xml
<artifactId>spring-boot-starter-data-jpa</artifactId>
```

**O que é:** Starter que inclui Hibernate e Spring Data JPA.

**Por que usar:** Embora o projeto use `JdbcTemplate` diretamente (sem entities JPA), esta dependência configura o `DataSource` e o `EntityManager` automaticamente. O `JdbcTemplate` é injetado automaticamente quando um `DataSource` está disponível.

### 3.5. `spring-boot-starter-mail`

```xml
<artifactId>spring-boot-starter-mail</artifactId>
```

**O que é:** Starter que inclui JavaMailSender para envio de e-mails.

**Por que usar:** Necessário para o fluxo de recuperação de senha, que envia um e-mail com o link de redefinição.

### 3.6. `springdoc-openapi-starter-webmvc-ui`

```xml
<groupId>org.springdoc</groupId>
<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
<version>3.1.1</version>
```

**O que é:** Biblioteca que gera documentação OpenAPI 3.0 automaticamente e fornece a interface Swagger UI.

**Por que usar:** Permite testar a API diretamente pelo navegador em `/swagger-ui.html` e gera a especificação OpenAPI em `/v3/api-docs`.

### 3.7. `spring-boot-h2console`

```xml
<artifactId>spring-boot-h2console</artifactId>
```

**O que é:** Console web para inspecionar o banco de dados H2 em tempo real.

**Por que usar:** Facilita o desenvolvimento e debug — acesse em `/h2-console` para ver as tabelas e dados.

### 3.8. `spring-boot-devtools`

```xml
<artifactId>spring-boot-devtools</artifactId>
<scope>runtime</scope>
<optional>true</optional>
```

**O que é:** Ferramentas de desenvolvimento que reiniciam automaticamente a aplicação quando código é modificado.

**Por que usar:** Acelera o ciclo de desenvolvimento. O `scope=runtime` e `optional=true` garantem que não seja incluído em produção.

### 3.9. Drivers de Banco de Dados

```xml
<artifactId>h2</artifactId>           <!-- scope: runtime -->
<artifactId>postgresql</artifactId>    <!-- scope: runtime -->
```

**Por que os dois?** H2 é usado para desenvolvimento local rápido (in-memory). PostgreSQL é o banco de produção. O `scope=runtime` significa que os drivers são necessários apenas em execução, não em compilação.

### 3.10. Dependências de Teste

```xml
<artifactId>spring-boot-starter-data-jpa-test</artifactId>     <!-- scope: test -->
<artifactId>spring-boot-starter-security-test</artifactId>     <!-- scope: test -->
<artifactId>spring-boot-starter-webmvc-test</artifactId>       <!-- scope: test -->
```

**Por que `scope=test`?** Essas dependências são usadas apenas nos testes unitários/integração e não são empacotadas no JAR final.

---

## 4. Configurações Externas (application.properties)

### 4.1. DataSource — Conexão com o Banco

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/store_land
spring.datasource.username=postgres
spring.datasource.password=1234
spring.datasource.driver-class-name=org.postgresql.Driver
```

**O que é:** Configura a conexão JDBC com o PostgreSQL.

**Por que `driver-class-name`?** Explicitamente informa qual driver JDBC usar. Embora o Spring Boot geralmente detecte automaticamente, declarar explicitamente evita ambiguidade quando H2 e PostgreSQL estão ambos no classpath.

### 4.2. JPA/Hibernate

```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

**`ddl-auto=update`:** O Hibernate compara o schema do banco com as entidades e aplica alterações (ADD COLUMN, CREATE TABLE, etc.). Em produção, o ideal seria `validate` ou `none`.

**`show-sql=true`:** Imprime no console todas as SQL executadas. Útil para debug durante desenvolvimento.

### 4.3. Mail — Configuração SMTP

```properties
spring.mail.host=${MAIL_HOST:localhost}
spring.mail.port=${MAIL_PORT:1025}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=${MAIL_SMTP_AUTH:false}
spring.mail.properties.mail.smtp.starttls.enable=${MAIL_SMTP_STARTTLS:false}
```

**O que é `${VARIAVEL:default}`?** Sintaxe de **variáveis de ambiente com valor padrão**. Se a variável `MAIL_HOST` não estiver definida no ambiente, usa `localhost`.

**Por que usar variáveis de ambiente?**
- **Segurança:** Senhas e credenciais não ficam hardcoded no código.
- **Flexibilidade:** Diferentes ambientes (dev, staging, produção) usam configurações diferentes sem alterar o código.
- **Portabilidade:** O mesmo JAR roda em qualquer ambiente com as variáveis corretas.

**Por que porta 1025?** É a porta padrão do **MailHog**, uma ferramenta de teste de e-mail que captura os e-mails localmente sem enviá-los de verdade.

### 4.4. Configurações Personalizadas

```properties
app.frontend-url=${FRONTEND_URL:http://localhost:3000}
app.mail.from=${MAIL_FROM:no-reply@store-land.local}
```

**O que é:** Propriedades customizadas com prefixo `app.`. São acessadas no código via `@Value("${app.frontend-url}")`.

**Por que customizadas?** O Spring Boot não tem propriedades nativas para "URL do frontend" ou "e-mail remetente". Criar propriedades customizadas mantém essas configurações externas e fáceis de alterar.

### 4.5. Segurança de Cookies de Sessão

```properties
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
server.servlet.session.cookie.secure=${SESSION_COOKIE_SECURE:false}
```

| Propriedade | O que faz | Por que |
|---|---|---|
| `http-only=true` | Impede que JavaScript acesse o cookie via `document.cookie` | Protege contra XSS (Cross-Site Scripting) |
| `same-site=lax` | Restringe envio do cookie em requisições cross-site | Protege contra CSRF (com exceção de navegação top-level) |
| `secure=false` (dev) | Cookie enviado em HTTP (não apenas HTTPS) | Em produção deve ser `true` para exigir HTTPS |

### 4.6. Swagger/OpenAPI

```properties
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.enabled=${SWAGGER_UI_ENABLED:true}
springdoc.api-docs.enabled=${SWAGGER_UI_ENABLED:true}
```

**Por que permitir desabilitar via variável?** Em produção, a documentação da API pode ser desativada por segurança, expondo menos detalhes da implementação.

---

## 5. Classe Principal — SpringBootApplication

```java
package br.com.store_land;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StoreLandApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreLandApplication.class, args);
    }
}
```

### `@SpringBootApplication`

**O que é:** Uma **meta-anotação** que combina três anotações em uma:

| Anotação combinada | Função |
|---|---|
| `@Configuration` | Marca a classe como fonte de beans do Spring |
| `@EnableAutoConfiguration` | Ativa a auto-configuração do Spring Boot com base nas dependências presentes |
| `@ComponentScan` | Escaneia o package `br.com.store_land` e sub-packages em busca de beans (`@Controller`, `@Service`, `@Component`, `@Configuration`) |

**Por que usar:** É a convenção padrão do Spring Boot. Uma única anotação substitui três, mantendo o código limpo.

**Por que o package `br.com.store_land`?** O `@ComponentScan` escaneia o package da classe anotada e todos os sub-packages. Por isso todos os outros pacotes (`config`, `controller`, `dto`) são sub-packages deste.

### `SpringApplication.run(...)`

**O que faz:**
1. Cria o **ApplicationContext** do Spring (contêiner de Injeção de Dependência).
2. Carrega todos os beans configurados.
3. Inicia o servidor Tomcat embutido.
4. Registra os hooks de shutdown graceful.

---

## 6. Configurações — Package `config`

### 6.1 CorsConfig — Cross-Origin Resource Sharing

```java
@Configuration
public class CorsConfig {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

#### `@Configuration`

**O que é:** Marca a classe como uma **classe de configuração** do Spring. O Spring processa os métodos anotados com `@Bean` e registra os objetos retornados no ApplicationContext.

**Por que usar:** Substitui arquivos XML de configuração do Spring por código Java puro (JavaConfig). É o padrão moderno do Spring Boot.

#### `@Value("${app.frontend-url}")`

**O que é:** Injeta o valor da propriedade `app.frontend-url` do `application.properties` no campo.

**Por que usar:** Em vez de hardcodear a URL do frontend (`http://localhost:3000`), o valor vem da configuração externa. Isso permite trocar a URL sem recompilar.

#### `@Bean`

**O que é:** Marca um método como produtor de um bean gerenciado pelo Spring. O objeto retornado pelo método é registrado no ApplicationContext.

**Por que usar:** O Spring Security precisa de um `CorsConfigurationSource` para funcionar com CORS. Ao declarar como `@Bean`, o Spring injeta automaticamente essa configuração na cadeia de filtros de segurança.

#### Método sem modificador de acesso (package-private)

```java
@Bean
CorsConfigurationSource corsConfigurationSource() {  // sem public
```

**Por que não `public`?** No Spring Boot, métodos `@Bean` em classes `@Configuration` podem ser package-private. É uma convenção que sinaliza "este bean é interno à configuração".

#### `CorsConfiguration` — O que cada método faz:

| Método | Função | Por que |
|---|---|---|
| `setAllowedOrigins(List.of(frontendUrl))` | Restringe a origem permitida | Não usa `*` porque `allowCredentials=true` exige origens explícitas |
| `setAllowedMethods(...)` | Define os HTTP methods permitidos | Apenas GET, POST e OPTIONS (preflight) são necessários |
| `setAllowedHeaders(...)` | Define os headers permitidos | `Content-Type` para JSON, `X-XSRF-TOKEN` para CSRF |
| `setAllowCredentials(true)` | Permite cookies na requisição cross-origin | Necessário porque a autenticação usa cookies de sessão |

#### `UrlBasedCorsConfigurationSource`

**O que é:** Aplica a configuração CORS a padrões de URL.

**Por que `"/**"`?** Aplica a configuração CORS a **todos** os endpoints da API.

---

### 6.2 SecurityConfig — Spring Security

Este é o arquivo mais complexo e importante do projeto.

```java
@Configuration
public class SecurityConfig {
```

#### `@Bean` — PasswordEncoder

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**`BCryptPasswordEncoder`:** Algoritmo de hashing de senhas que:
- Gera um **salt** aleatório automaticamente para cada senha.
- É **lento por design** (custo computacional alto), o que dificulta ataques de força bruta.
- É **one-way** (irreversível) — só é possível comparar, não recuperar a senha original.

**Por que BCrypt?** É o padrão da indústria para hashing de senhas. Alternativas como MD5 ou SHA-256 simples são inseguras porque são muito rápidas.

#### `@Bean` — UserDetailsService

```java
@Bean
UserDetailsService userDetailsService(JdbcTemplate jdbcTemplate) {
    return username -> jdbcTemplate.query("""
                    SELECT username, password_hash, is_active
                    FROM users
                    WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)
                    """,
            (resultSet, ResultSetMapper) -> User.withUsername(resultSet.getString("username"))
                    .password(resultSet.getString("password_hash"))
                    .authorities("ROLE_USER")
                    .disabled(!resultSet.getBoolean("is_active"))
                    .build(),
            username, username
    ).stream().findFirst().orElseThrow(
            () -> new UsernameNotFoundException("Usuário ou senha inválidos.")
    );
}
```

**O que é `UserDetailsService`:** Interface do Spring Security responsável por carregar os dados do usuário para autenticação.

**Por que usar `JdbcTemplate` em vez de JPA Repository?**
- **Simplicidade:** Uma consulta SQL direta é mais simples que criar Entity + Repository + Service.
- **Performance:** Sem overhead de ORM (Hibernate) para uma consulta simples.
- **Controle total:** O desenvolvedor escreve a SQL exata que quer.

**Text Blocks (`""" ... """`):**
- Introduzido no Java 15. Permite strings multi-linha sem necessidade de `\n` ou `+`.
- Mantém a SQL legível e formatada.

**`LOWER(username) = LOWER(?)`:**
- Torna a autenticação **case-insensitive**. O usuário pode digitar "Admin", "admin" ou "ADMIN" e funciona.
- O `?` é um **parâmetro preparado** (prepared statement) que previne **SQL Injection**.

**`User.withUsername(...).password(...).authorities(...).build()`:**
- **Builder pattern** do Spring Security para criar um objeto `User` (representação do usuário autenticado).
- `authorities("ROLE_USER")`: Atribui a permissão padrão. Todos os usuários têm o mesmo role neste projeto.
- `disabled(!is_active)`: Se `is_active = false` no banco, o usuário não pode fazer login.

**`.stream().findFirst().orElseThrow(...)`:**
- Se nenhum usuário for encontrado, lança `UsernameNotFoundException`.
- A mensagem genérica "Usuário ou senha inválidos" é intencional — não revela se o usuário existe ou não (previne **user enumeration**).

#### `@Bean` — AuthenticationManager

```java
@Bean
AuthenticationManager authenticationManager(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
) {
    DaoAuthenticationProvider authenticationProvider =
            new DaoAuthenticationProvider(userDetailsService);
    authenticationProvider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(List.of(authenticationProvider));
}
```

**O que é `AuthenticationManager`:** O coordenador central da autenticação no Spring Security. Recebe credenciais e retorna um objeto `Authentication` com o usuário autenticado.

**Por que expor como `@Bean`?** Por padrão, o Spring Security cria um `AuthenticationManager` interno. Ao expô-lo como bean, o `AuthController` pode injetá-lo e chamar `authenticate()` manualmente nos endpoints de login e registro.

**`DaoAuthenticationProvider`:** Usa o `UserDetailsService` (DAO = Data Access Object) para carregar os dados do usuário e o `PasswordEncoder` para verificar a senha.

**Injeção por parâmetro do método `@Bean`:**
```java
UserDetailsService userDetailsService,  // Spring injeta o bean já criado
PasswordEncoder passwordEncoder        // Spring injeta o bean já criado
```
O Spring resolve automaticamente as dependências entre beans declarados na mesma classe `@Configuration`.

#### `@Bean` — SecurityContextRepository

```java
@Bean
SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
}
```

**O que é:** Define onde o `SecurityContext` (informação de quem está autenticado) é armazenado entre requisições.

**Por que `HttpSession`:** O contexto de segurança é salvo na sessão HTTP (cookie JSESSIONID). Em cada requisição, o Spring Security restaura o contexto a partir da sessão.

#### `@Bean` — SessionAuthenticationStrategy

```java
@Bean
SessionAuthenticationStrategy sessionAuthenticationStrategy() {
    return new ChangeSessionIdAuthenticationStrategy();
}
```

**O que é:** Estratégia executada após uma autenticação bem-sucedida.

**Por que `ChangeSessionId`:** Após o login, o ID da sessão é **regenerado**. Isso previne **Session Fixation Attack** — um ataque onde o atacante fixa uma sessão conhecida antes do login da vítima.

#### `@Bean` — SecurityFilterChain

```java
@Bean
SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SecurityContextRepository securityContextRepository
) throws Exception {
```

**O que é:** Define a cadeia de filtros de segurança — a espinha dorsal do Spring Security Web.

**Por que substituir `WebSecurityConfigurerAdapter`?** A partir do Spring Security 5.7, o `WebSecurityConfigurerAdapter` foi deprecated. O padrão moderno é criar beans `SecurityFilterChain` diretamente.

##### CORS

```java
.cors(Customizer.withDefaults())
```

**O que faz:** Habilita o CORS usando a configuração do bean `CorsConfigurationSource` definido em `CorsConfig`.

**`Customizer.withDefaults()`:** Usa a configuração padrão encontrada no ApplicationContext. É uma forma concisa de referenciar o bean já configurado.

##### CSRF

```java
.csrf(csrf -> csrf
        .csrfTokenRepository(csrfTokenRepository)
        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
)
```

**`CookieCsrfTokenRepository.withHttpOnlyFalse()`:**
- Armazena o token CSRF em um cookie chamado `XSRF-TOKEN`.
- `withHttpOnlyFalse()` é **intencional**: o JavaScript do frontend precisa ler o cookie para enviá-lo de volta no header `X-XSRF-TOKEN`.

**Por que `HttpOnly=false` só para este cookie?** O cookie de sessão (`JSESSIONID`) mantém `HttpOnly=true`. Apenas o cookie CSRF precisa ser lido pelo JS, e como ele é usado apenas como um "espelho" (o servidor o coloca no cookie e o verifica no header), o risco é mínimo.

**`CsrfTokenRequestAttributeHandler`:**
- Faz o token CSRF estar disponível como atributo da requisição.
- Permite que o endpoint `GET /api/auth/csrf` retorne o token ao cliente.

##### Security Context

```java
.securityContext(context -> context
        .securityContextRepository(securityContextRepository)
)
```

**O que faz:** Usa o `HttpSessionSecurityContextRepository` definido acima para salvar/carregar o contexto de segurança.

##### Autorização de Endpoints

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(
                "/api/auth/csrf",
                "/api/auth/register",
                "/api/auth/login",
                "/api/auth/forgot-password",
                "/api/auth/reset-password",
                "/api/teste",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**"
        ).permitAll()
        .anyRequest().authenticated()
)
```

**`requestMatchers(...).permitAll()`:**
- Lista de endpoints que **não exigem autenticação**.
- Inclui: registro, login, recuperação de senha, CSRF token, Swagger UI.

**`anyRequest().authenticated()`:**
- **Todos os outros endpoints** exigem autenticação.
- É a regra "catch-all" que protege qualquer endpoint não listado acima.

**Por que listar explicitamente?** É o princípio de **menor privilégio**: apenas o necessário é aberto, tudo mais é protegido por padrão.

##### Desabilitando Login/Logout Padrão

```java
.formLogin(AbstractHttpConfigurer::disable)
.httpBasic(AbstractHttpConfigurer::disable)
.logout(AbstractHttpConfigurer::disable);
```

**Por que desabilitar?**
- `formLogin`: O Spring Security cria automaticamente uma página de login HTML. Como este é uma API REST, não há páginas HTML.
- `httpBasic`: Autenticação Basic (header `Authorization: Basic ...`) não é adequada para APIs com cookies.
- `logout`: O logout padrão do Spring (`/logout`) é substituído pelo endpoint customizado `POST /api/auth/logout` no `AuthController`, que limpa o contexto de segurança manualmente.

**`AbstractHttpConfigurer::disable`:**
- **Method reference** do Java 8. Equivalente a `x -> x.disable()`.
- Sintaxe mais concisa e legível.

---

### 6.3 OpenApiConfig — Documentação Swagger/OpenAPI

```java
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI storeLandOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Store Land API")
                        .description("""
                                API de autenticação do Store Land.

                                Para endpoints POST, primeiro chame GET /api/auth/csrf.
                                Envie o token retornado no cabeçalho X-XSRF-TOKEN e
                                mantenha os cookies recebidos, incluindo JSESSIONID.
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("sessionCookie",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("JSESSIONID")
                                        .description("Cookie de sessão criado após autenticação."))
                        .addSecuritySchemes("csrfToken",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-XSRF-TOKEN")
                                        .description("Token obtido em GET /api/auth/csrf.")));
    }
```

#### Builder Fluent do OpenAPI

**O que é:** A API do `OpenAPI` usa o **padrão Builder Fluent** — cada método retorna `this`, permitindo encadear chamadas.

**Por que usar:** Código mais legível e compacto. Em vez de:
```java
OpenAPI openApi = new OpenAPI();
Info info = new Info();
info.setTitle("...");
openApi.info(info);
```
Usa-se:
```java
new OpenAPI().info(new Info().title("..."))
```

#### Security Schemes

**`sessionCookie`:** Define que o Swagger UI deve enviar o cookie `JSESSIONID` para endpoints protegidos.

**`csrfToken`:** Define que o Swagger UI deve enviar o header `X-XSRF-TOKEN` para endpoints POST.

**Por que definir schemes?** Sem isso, o Swagger UI não saberia como autenticar nas requisições de teste.

#### OpenApiCustomizer

```java
@Bean
OpenApiCustomizer logoutSecurityRequirements() {
    return openApi -> {
        var logoutPath = openApi.getPaths().get("/api/auth/logout");
        if (logoutPath != null && logoutPath.getPost() != null) {
            logoutPath.getPost().setSecurity(List.of(
                    new SecurityRequirement()
                            .addList("sessionCookie")
                            .addList("csrfToken")
            ));
        }
    };
}
```

**O que é `OpenApiCustomizer`:** Um callback executado após a geração automática da documentação OpenAPI. Permite modificar programaticamente a documentação.

**Por que usar aqui?** O endpoint de logout precisa de **ambos** os security schemes (cookie + CSRF). O customizer garante que isso fique explícito na documentação.

**Lambda `openApi -> { ... }`:**
- Sintaxe lambda do Java 8.
- O tipo `OpenApiCustomizer` é uma **Functional Interface** (tem apenas um método abstrato).

---

## 7. Controllers — Package `controller`

### 7.1 AuthController — Endpoints de Autenticação

#### Anotações da Classe

```java
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Cadastro, login e recuperação de acesso.")
public class AuthController {
```

##### `@RestController`

**O que é:** Combina `@Controller` + `@ResponseBody`.

| Anotação | Função |
|---|---|
| `@Controller` | Registra a classe como um controller MVC — o Spring mapeia as requisições HTTP para os métodos |
| `@ResponseBody` | Serializa automaticamente o retorno dos métodos para JSON (via Jackson) |

**Por que `@RestController` em vez de `@Controller`?**
- Em APIs REST, **todos** os métodos retornam JSON. `@RestController` elimina a necessidade de `@ResponseBody` em cada método.
- `@Controller` seria usado se houvesse views HTML (Thymeleaf, JSP).

##### `@RequestMapping("/api/auth")`

**O que é:** Define o **prefixo de URL** comum para todos os métodos do controller.

**Por que usar:** Evita repetição. Em vez de `@PostMapping("/api/auth/login")`, `@PostMapping("/api/auth/register")`, etc., define-se uma vez na classe e usa `@PostMapping("/login")`, `@PostMapping("/register")` nos métodos.

##### `@Tag(name = "...", description = "...")`

**O que é:** Anotação do **OpenAPI/Swagger** que agrupa endpoints na documentação.

**Por que usar:** No Swagger UI, os endpoints são organizados por tags. Todos os métodos deste controller aparecerão sob a seção "Autenticação".

#### Logger e Constantes

```java
private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);
private static final SecureRandom SECURE_RANDOM = new SecureRandom();
private static final String GENERIC_RESET_MESSAGE =
        "Se o e-mail estiver cadastrado, enviaremos instruções para redefinir sua senha.";
```

##### `Logger` (SLF4J)

**`private static final Logger`:**
- `static`: Uma única instância por classe, não por objeto.
- `final`: Não pode ser reatribuído.
- O padrão `LoggerFactory.getLogger(Classe.class)` é a forma padrão do **SLF4J** (Simple Logging Facade for Java).

**Por que SLF4J?** É uma fachada (facade) que permite trocar a implementação de logging (Logback, Log4j2) sem alterar o código. O Spring Boot usa Logback por padrão.

##### `SecureRandom`

**O que é:** Gerador de números aleatórios **criptograficamente seguro**.

**Por que não `java.util.Random`?** `Random` é previsível — um atacante pode adivinhar os valores. `SecureRandom` usa fontes de entropia do sistema operacional, tornando os valores imprevisíveis. É essencial para tokens de segurança.

**`static final`:** O `SecureRandom` é thread-safe e pode ser compartilhado entre todas as instâncias do controller.

##### Mensagem Genérica de Reset

**Por que a mensagem não revela se o e-mail existe?**
- Se a mensagem dissesse "E-mail não encontrado", um atacante poderia testar e-mails um por um para descobrir quais estão cadastrados (**user enumeration attack**).
- A mensagem genérica é idêntica independente de o e-mail existir ou não.

#### Injeção de Dependências via Construtor

```java
public AuthController(
        JdbcTemplate jdbcTemplate,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        SecurityContextRepository securityContextRepository,
        SessionAuthenticationStrategy sessionAuthenticationStrategy,
        JavaMailSender mailSender,
        @Value("${app.frontend-url}") String frontendUrl,
        @Value("${app.mail.from}") String mailFrom
) {
    this.jdbcTemplate = jdbcTemplate;
    // ...
}
```

**O que é:** **Constructor Injection** (Injeção via Construtor) — o Spring Boot chama o construtor e passa os beans automaticamente.

**Por que construtor em vez de `@Autowired` nos campos?**
| Vantagem | Explicação |
|---|---|
| **Imutabilidade** | Campos são `final` — não podem ser nulos após construção |
| **Testabilidade** | Facilita testes unitários — basta criar o objeto com mocks |
| **Clareza** | Dependências são visíveis no construtor, não espalhadas pela classe |
| **Ciclo de vida** | O objeto está completamente pronto assim que é criado |

**`@Value` no parâmetro do construtor:**
- Permite injetar valores de propriedades diretamente no construtor.
- Equivalente a ter um campo `@Value`, mas integrado ao padrão de injeção via construtor.

#### Endpoint: GET /api/auth/csrf

```java
@GetMapping("/csrf")
@Operation(
        summary = "Obter token CSRF",
        description = "Obtém um token CSRF e o cookie XSRF-TOKEN necessários para chamadas POST."
)
public CsrfResponse csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
    return new CsrfResponse(csrfToken.getToken());
}
```

##### `@GetMapping("/csrf")`

**O que é:** Atalho para `@RequestMapping(path = "/csrf", method = RequestMethod.GET)`.

**Por que GET para CSRF?** O token CSRF precisa ser obtido **antes** de qualquer POST. Uma requisição GET é segura (idempotente) e não precisa do próprio token CSRF.

**URL completa:** `GET /api/auth/csfs` (prefixo da classe + path do método).

##### `@Operation(summary = "...", description = "...")`

**O que é:** Anotação OpenAPI que documenta um endpoint individual.

**Por que usar:** Gera a documentação automática no Swagger UI com título e descrição claros.

##### `@Parameter(hidden = true)`

**O que é:** Anotação OpenAPI que esconde um parâmetro da documentação.

**Por que esconder `CsrfToken`?** O Spring Security injeta o `CsrfToken` automaticamente a partir do atributo da requisição. Não é um parâmetro que o cliente envia — é resolvido pelo servidor. Mostrar na documentação confundiria os usuários da API.

##### `CsrfResponse` — Record Interno

```java
public record CsrfResponse(String token) {}
```

**O que é `record`:** Introduzido no **Java 16** (feature preview) e **Java 17** (estável). É uma classe imutável projetada para transportar dados.

**Por que usar records em vez de classes?**
| Record | Classe tradicional |
|---|---|
| Zero boilerplate | Requer constructor, getters, equals, hashCode, toString |
| Imutável por padrão | Campos mutáveis por padrão |
| Sem herança | Pode herdar de outras classes |
| Sem estado adicional | Pode ter campos não relacionados ao constructor |

**Por que `CsrfResponse` é interno ao controller?** É usado apenas neste endpoint. Torná-lo um record interno evita poluir o package `dto`.

#### Endpoint: POST /api/auth/register

```java
@PostMapping("/register")
@Transactional
@Operation(summary = "Criar conta", description = "Cadastra o usuário e inicia uma sessão.")
@SecurityRequirement(name = "csrfToken")
public ResponseEntity<AuthResponse> register(
        @RequestBody RegisterRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
) {
```

##### `@PostMapping("/register")`

**O que é:** Atalho para `@RequestMapping(path = "/register", method = RequestMethod.POST)`.

**Por que POST?** O registro **cria** um recurso no servidor. POST é o método HTTP correto para operações que modificam o estado do servidor.

##### `@Transactional`

**O que é:** Anotação do Spring que garante que o método execute dentro de uma **transação de banco de dados**.

**Por que usar aqui?** O registro faz duas operações:
1. `INSERT INTO users`
2. Autenticação (que pode ter efeitos colaterais)

Se a segunda falhar, a primeira é **rollbackada** — o usuário não fica meio cadastrado.

**Comportamento da transação:**
- `INSERT` + sucesso → `COMMIT`
- `INSERT` + erro → `ROLLBACK`
- `INSERT` + sucesso + autenticação falha → `ROLLBACK` de tudo

##### `@SecurityRequirement(name = "csrfToken")`

**O que é:** Anotação OpenAPI que indica que este endpoint requer o header `X-XSRF-TOKEN`.

**Por que usar:** Documenta no Swagger UI que o token CSRF é obrigatório. O Swagger UI incluirá automaticamente o header ao testar o endpoint.

##### `@RequestBody RegisterRequest request`

**O que é:** Anotação que diz ao Spring para **deserializar** o corpo JSON da requisição no objeto `RegisterRequest`.

**Como funciona:**
1. Cliente envia: `{"fullName": "João", "username": "joao", ...}`
2. Spring usa **Jackson** (biblioteca JSON) para converter o JSON no record `RegisterRequest`
3. O objeto é passado como parâmetro do método

**Por que usar record como `@RequestBody`?** O record `RegisterRequest` define os campos esperados. Se o JSON tiver campos extras, são ignorados. Se faltar um campo obrigatório, o campo será `null` (validação manual no código).

##### `HttpServletRequest` e `HttpServletResponse`

**O que são:** Objetos Servlet que dão acesso direto à requisição e resposta HTTP.

**Por que injetá-los?**
- `HttpServletRequest`: Para acessar/criar a sessão (`request.getSession(true)`).
- `HttpServletResponse`: Para salvar o contexto de segurança nos cookies de resposta.

O Spring detecta esses parâmetros especiais e os injeta automaticamente — não precisam de anotação.

##### Validação Manual

```java
String fullName = requiredText(request.fullName(), "Nome completo");
String username = requiredText(request.username(), "Usuário");
String email = requiredText(request.email(), "E-mail").toLowerCase(Locale.ROOT);
String password = validatePassword(request.password());
```

**Por que validação manual em vez de Bean Validation (`@Valid`, `@NotBlank`)?**
- O projeto escolheu validação manual para ter **mensagens de erro personalizadas** em português.
- Bean Validation (`jakarta.validation`) gera mensagens em inglês por padrão e requer configuração adicional para internacionalização.
- A validação manual permite regras complexas (tamanho em bytes UTF-8, regex customizado).

##### `ResponseStatusException`

```java
throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "As senhas não coincidem."
);
```

**O que é:** Exceção do Spring Web que converte automaticamente em uma resposta HTTP com o status code especificado.

**Por que usar?**
- `HttpStatus.BAD_REQUEST` → Resposta HTTP 400
- `HttpStatus.CONFLICT` → Resposta HTTP 409
- Mais semântico que `throw new RuntimeException(...)`

##### Tratamento de Chave Duplicada

```java
} catch (DuplicateKeyException exception) {
    throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Este usuário ou e-mail já está cadastrado."
    );
}
```

**`DuplicateKeyException`:** Exceção do Spring Data lançada quando uma `UNIQUE CONSTRAINT` do banco é violada.

**Por que capturar esta exceção específica?** Em vez de deixar o erro do banco vazar para o cliente (com detalhes internos), converte-se em uma resposta HTTP 409 limpa.

##### Autenticação Automática Após Registro

```java
Authentication authentication = authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(username, password)
);
saveAuthentication(authentication, servletRequest, servletResponse);
return ResponseEntity.status(HttpStatus.CREATED)
        .body(new AuthResponse(findUser(authentication.getName())));
```

**Fluxo:**
1. `authenticationManager.authenticate(...)`: Verifica as credenciais recém-criadas.
2. `saveAuthentication(...)`: Cria a sessão HTTP e salva o contexto de segurança.
3. `HttpStatus.CREATED` (201): Status HTTP correto para recurso criado.

**Por que autenticar automaticamente?** Melhor experiência do usuário — após se registrar, o usuário já está logado sem precisar fazer login novamente.

#### Endpoint: POST /api/auth/login

```java
@PostMapping("/login")
@Operation(summary = "Entrar", description = "Autentica por usuário ou e-mail e inicia uma sessão.")
@SecurityRequirement(name = "csrfToken")
public AuthResponse login(
        @RequestBody LoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
) {
```

**Diferença do registro:** Retorna `AuthResponse` diretamente (sem `ResponseEntity`). O Spring usa `HttpStatus.OK` (200) por padrão.

**Por que `AuthResponse` sem `ResponseEntity`?** Quando não é necessário controlar o status HTTP ou headers manualmente, retornar o objeto diretamente é mais conciso.

#### Endpoint: GET /api/auth/me

```java
@GetMapping("/me")
@Operation(summary = "Consultar sessão", description = "Retorna os dados do usuário autenticado.")
@SecurityRequirement(name = "sessionCookie")
public AuthResponse currentUser(Authentication authentication) {
    return new AuthResponse(findUser(authentication.getName()));
}
```

**`Authentication authentication`:** O Spring Security injeta automaticamente o objeto de autenticação do usuário atual.

**Por que este parâmetro funciona?** O Spring MVC tem um **HandlerMethodArgumentResolver** especial para `Authentication`. Quando vê este tipo no parâmetro, busca no `SecurityContextHolder`.

**`@SecurityRequirement(name = "sessionCookie")`:** Indica que apenas o cookie de sessão é necessário (sem CSRF, pois é GET).

#### Endpoint: POST /api/auth/logout

```java
@PostMapping("/logout")
@Operation(summary = "Sair", description = "Encerra a sessão atual.")
@SecurityRequirement(name = "sessionCookie")
@SecurityRequirement(name = "csrfToken")
public MessageResponse logout(HttpServletRequest request) {
    SecurityContextHolder.clearContext();
    if (request.getSession(false) != null) {
        request.getSession(false).invalidate();
    }
    return new MessageResponse("Sessão encerrada.");
}
```

**`SecurityContextHolder.clearContext()`:** Remove o contexto de segurança do thread atual.

**`request.getSession(false).invalidate()`:**
- `getSession(false)`: Retorna a sessão se existir, `null` se não existir (não cria uma nova).
- `invalidate()`: Destrói a sessão no servidor. O cookie `JSESSIONID` deixa de ser válido.

**Por que `getSession(false)` em vez de `getSession(true)`?** `getSession(true)` criaria uma nova sessão se não houver uma — comportamento indesejado no logout.

**Por que dois `@SecurityRequirement`?** O logout precisa do cookie de sessão (para saber quem está saindo) e do CSRF token (porque é POST — operação mutável).

#### Endpoint: POST /api/auth/forgot-password

```java
@PostMapping("/forgot-password")
@Transactional
@Operation(
        summary = "Solicitar redefinição de senha",
        description = "Envia instruções por e-mail quando o endereço estiver cadastrado."
)
@SecurityRequirement(name = "csrfToken")
public MessageResponse forgotPassword(@RequestBody ForgotPasswordRequest request) {
```

##### Geração do Token

```java
byte[] randomToken = new byte[32];
SECURE_RANDOM.nextBytes(randomToken);
String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
String tokenHash = sha256(token);
```

**Fluxo de segurança:**
1. Gera 32 bytes aleatórios criptograficamente seguros (~256 bits de entropia).
2. Codifica em **Base64 URL-safe** (sem `+`, `/`, `=`) — seguro para URLs.
3. **Hash SHA-256** do token antes de salvar no banco.

**Por que salvar apenas o hash?**
- Se o banco for comprometido, os tokens não podem ser reconstruídos (SHA-256 é one-way).
- O token real só existe na URL enviada por e-mail e na memória do cliente.

**Por que `withoutPadding()`?** Remove os `=` do final do Base64, tornando a URL mais limpa.

##### Expiração do Token

```java
Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
```

**Por que 30 minutos?** Balanceia segurança (janela pequena) e usabilidade (tempo suficiente para clicar no link).

##### Inserção no Banco

```java
jdbcTemplate.update("""
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """,
        recipient.id(), tokenHash, Timestamp.from(expiresAt)
);
```

**`jdbcTemplate.update()`:** Executa SQL que não retorna resultados (INSERT, UPDATE, DELETE).

**`Timestamp.from(expiresAt)`:** Converte `java.time.Instant` em `java.sql.Timestamp` para compatibilidade JDBC.

##### Envio de E-mail

```java
SimpleMailMessage message = new SimpleMailMessage();
message.setFrom(mailFrom);
message.setTo(recipient.email());
message.setSubject("Redefinição de senha - Store Land");
message.setText("""...""");
try {
    mailSender.send(message);
} catch (MailException exception) {
    jdbcTemplate.update(
            "DELETE FROM password_reset_tokens WHERE token_hash = ?",
            tokenHash
    );
    LOGGER.error(
            "SMTP não conseguiu enviar o e-mail de recuperação ({}).",
            exception.getClass().getSimpleName()
    );
}
```

**Por que deletar o token se o e-mail falhar?** Evita acumular tokens inúteis no banco. Se o e-mail não foi enviado, o token nunca será usado.

**Por que registrar apenas o `getSimpleName()` da exceção?** Evita vazar detalhes sensíveis do stack trace para logs.

**Por que a mensagem de sucesso é genérica?** Mesmo se o e-mail não estiver cadastrado, a resposta é a mesma — previne user enumeration.

#### Endpoint: POST /api/auth/reset-password

```java
@PostMapping("/reset-password")
@Transactional
@Operation(summary = "Redefinir senha", description = "Redefine a senha usando um token válido.")
@SecurityRequirement(name = "csrfToken")
public MessageResponse resetPassword(@RequestBody ResetPasswordRequest request) {
```

##### Consulta com `FOR UPDATE`

```java
var tokens = jdbcTemplate.query("""
                SELECT user_id
                FROM password_reset_tokens
                WHERE token_hash = ?
                  AND expires_at > CURRENT_TIMESTAMP
                  AND used_at IS NULL
                  FOR UPDATE
                  """,
```

**`FOR UPDATE`:** Cláusula SQL que **trava** as linhas selecionadas até o fim da transação.

**Por que usar?** Previne **race condition**: se o usuário clicar no link duas vezes simultaneamente, apenas uma transação consegue travar e usar o token. A outra falha porque não encontra linhas disponíveis.

**Condições de validação:**
| Condição | Protege contra |
|---|---|
| `token_hash = ?` | Token correto |
| `expires_at > CURRENT_TIMESTAMP` | Token não expirado |
| `used_at IS NULL` | Token não usado anteriormente |

##### Atualização da Senha e Marcação do Token

```java
jdbcTemplate.update("""
                UPDATE users
                SET password_hash = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
        passwordEncoder.encode(password), resetToken.userId()
);
jdbcTemplate.update("""
                UPDATE password_reset_tokens
                SET used_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND used_at IS NULL
                """,
        resetToken.userId()
);
```

**Por que `used_at IS NULL` no UPDATE?** Garante que apenas o **primeiro** uso marca o token. Usos subsequentes não fazem nada.

**`@Transactional`:** As duas UPDATEs estão na mesma transação. Se a segunda falhar, a primeira é rollbackada — consistência garantida.

#### Métodos Privados Auxiliares

##### `saveAuthentication`

```java
private void saveAuthentication(
        Authentication authentication,
        HttpServletRequest request,
        HttpServletResponse response
) {
    request.getSession(true);
    sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
}
```

**Passo a passo:**
1. `request.getSession(true)`: Cria uma nova sessão HTTP (ou retorna a existente).
2. `sessionAuthenticationStrategy.onAuthentication(...)`: Executa a estratégia configurada (`ChangeSessionId` — regenera o ID da sessão).
3. `SecurityContextHolder.createEmptyContext()`: Cria um novo contexto de segurança vazio.
4. `context.setAuthentication(...)`: Define quem está autenticado.
5. `SecurityContextHolder.setContext(context)`: Define o contexto no thread atual.
6. `securityContextRepository.saveContext(...)`: Persiste o contexto na sessão HTTP (cookie JSESSIONID).

**Por que este método é necessário?** O Spring Security não faz tudo isso automaticamente quando você chama `authenticationManager.authenticate()` manualmente. Este método completa o ciclo de autenticação.

##### `findUser`

```java
private UserDto findUser(String username) {
    return jdbcTemplate.queryForObject("""
                    SELECT id, full_name, email
                    FROM users
                    WHERE LOWER(username) = LOWER(?)
                    """,
            (resultSet, rowNumber) -> new UserDto(
                    resultSet.getLong("id"),
                    resultSet.getString("full_name"),
                    resultSet.getString("email")
            ),
            username
    );
}
```

**`jdbcTemplate.queryForObject()`:** Executa uma query que retorna **exatamente uma** linha.

**Por que não selecionar `password_hash`?** O DTO `UserDto` é enviado ao cliente. Nunca expor a senha hashada na API é uma boa prática de segurança.

##### `requiredText`

```java
private String requiredText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                fieldName + " é obrigatório."
        );
    }
    return value.trim();
}
```

**`value.isBlank()`:** Verifica se é `null`, vazio ou apenas espaços. Mais robusto que `value.isEmpty()`.

**`value.trim()`:** Remove espaços em branco do início e fim — previne problemas com " admin " vs "admin".

##### `validatePassword`

```java
private String validatePassword(String value) {
    String password = requiredText(value, "Senha");
    int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
    if (password.length() < 8 || passwordBytes > 72) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A senha deve ter pelo menos 8 caracteres e no máximo 72 bytes UTF-8."
        );
    }
    return password;
}
```

**Por que 72 bytes UTF-8?** O BCrypt tem um limite interno de 72 bytes. Senhas maiores são truncadas, o que significa que "senha123" e "senha123xxxx" teriam o mesmo hash. Validar no código evita essa ambiguidade.

**Por que validar em bytes, não caracteres?** Caracteres Unicode (emojis, acentos) podem ocupar múltiplos bytes em UTF-8. Validar em bytes garante que o limite do BCrypt é respeitado.

##### `sha256`

```java
private String sha256(String value) {
    try {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8))
        );
    } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException("SHA-256 não está disponível.", exception);
    }
}
```

**`MessageDigest.getInstance("SHA-256")`:** Algoritmo de hash criptográfico padrão.

**`HexFormat.of().formatHex(...)`:** Converte bytes em string hexadecimal. `HexFormat` foi introduzido no **Java 19** — mais simples que `DatatypeConverter` ou `String.format`.

**Por que `IllegalStateException`?** SHA-256 é obrigatório no Java SE. Se não estiver disponível, algo está gravemente errado com o ambiente — não é um erro de negócio.

#### Records Internos

```java
private record ResetRecipient(long id, String email) {}
private record ResetToken(long userId) {}
```

**Por que records privados?** São usados apenas dentro do controller para mapear resultados de query. Não precisam ser expostos como DTOs públicos.

---

### 7.2 ApiExceptionHandler — Tratamento Global de Erros

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<MessageResponse> handleAuthenticationException() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponse("Usuário ou senha inválidos."));
    }
}
```

#### `@RestControllerAdvice`

**O que é:** Combina `@ControllerAdvice` + `@ResponseBody`.

**Por que usar:** Permite interceptar exceções lançadas por **qualquer** `@RestController` do aplicativo — um ponto centralizado de tratamento de erros.

**Como funciona:**
1. Qualquer controller lança `AuthenticationException`
2. O Spring busca um `@ExceptionHandler` compatível
3. Este método captura e retorna uma resposta HTTP 401 formatada em JSON

**Por que não tratar no controller?** Centralizar o tratamento evita duplicação. Se houver 10 controllers e todos podem lançar `AuthenticationException`, um único `@RestControllerAdvice` trata todos.

#### `@ExceptionHandler(AuthenticationException.class)`

**O que é:** Declara que este método trata exceções do tipo `AuthenticationException` (e subclasses).

**Por que `AuthenticationException`?** É a exceção lançada pelo `AuthenticationManager` quando as credenciais estão erradas. Sem este handler, o Spring Security retornaria um erro 401 genérico sem corpo JSON.

#### `ResponseEntity.status(HttpStatus.UNAUTHORIZED)`

**Por que `ResponseEntity` aqui?** Diferente dos controllers que retornam o body diretamente, o handler precisa controlar explicitamente o status HTTP (401).

---

## 8. DTOs — Package `dto`

Todos os DTOs são **records** do Java:

```java
public record AuthResponse(UserDto user) {}
public record ForgotPasswordRequest(String email) {}
public record LoginRequest(String username, String password) {}
public record MessageResponse(String message) {}
public record RegisterRequest(String fullName, String username, String email, String password, String confirmPassword) {}
public record ResetPasswordRequest(String token, String password) {}
public record UserDto(Long id, String name, String email) {}
```

### Por que usar `record` para DTOs?

| Característica | Benefício |
|---|---|
| **Imutabilidade** | Dados não podem ser alterados após criação — thread-safe |
| **Zero boilerplate** | O Java gera automaticamente: construtor, getters, `equals()`, `hashCode()`, `toString()` |
| **Serialização JSON** | Jackson serializa records automaticamente — os componentes do construtor viram campos JSON |
| **Compacto** | Uma linha em vez de 30+ linhas de classe tradicional |

### Serialização Jackson com Records

Quando o Jackson serializa um record:
```java
new UserDto(1L, "João", "joao@email.com")
```
Vira JSON:
```json
{
    "id": 1,
    "name": "João",
    "email": "joao@email.com"
}
```

Os **getters** dos records (`id()`, `name()`, `email()`) são tratados como propriedades pelo Jackson.

### Por que separar Request e Response?

| DTO | Direção | Por que separado |
|---|---|---|
| `LoginRequest` | Cliente → Servidor | Contém senha — nunca deve aparecer em respostas |
| `AuthResponse` | Servidor → Cliente | Contém dados do usuário — sem senha |
| `UserDto` | Servidor → Cliente | Dados públicos do usuário |

Esta separação previne **vazamento acidental de dados sensíveis**. Se usássemos uma única classe `User` com todos os campos, seria fácil esquecer de excluir a senha da resposta.

---

## 9. Schema do Banco de Dados (schema.sql)

### Tabela `users`

```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL
        CHECK (length(btrim(full_name)) > 0),
    username VARCHAR(50) NOT NULL
        CHECK (username ~ '^[A-Za-z0-9_.-]{3,50}$'),
    email VARCHAR(254) NOT NULL
        CHECK (length(btrim(email)) > 0),
    password_hash TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### `BIGINT GENERATED ALWAYS AS IDENTITY`

**O que é:** Equivalente ao `AUTO_INCREMENT` do MySQL. O banco gera automaticamente o próximo ID sequencial.

**`ALWAYS`:** Impede `INSERT INTO users (id, ...) VALUES (42, ...)` — o ID é sempre gerado pelo banco.

**Por que `BIGINT`?** Suporta até 9 quintilhões de registros. Mais seguro que `INTEGER` (2 bilhões) para sistemas que podem crescer.

#### `CHECK` Constraints

**O que são:** Regras de validação executadas **no banco de dados**.

**Por que usar?**
- **Defesa em profundidade:** Mesmo que a validação do backend falhe, o banco rejeita dados inválidos.
- `CHECK (length(btrim(full_name)) > 0)`: Impede nomes em branco.
- `CHECK (username ~ '^[A-Za-z0-9_.-]{3,50}$')`: O `~` é o operador de **regex** do PostgreSQL. Garante que o username segue o padrão.

#### `TIMESTAMPTZ`

**O que é:** `TIMESTAMP WITH TIME ZONE` — armazena data/hora com fuso horário.

**Por que não `TIMESTAMP`?** `TIMESTAMP` (sem timezone) armazena a hora "cega" — sem informação de fuso. `TIMESTAMPTZ` converte automaticamente para UTC e preserva o fuso original.

#### `DEFAULT CURRENT_TIMESTAMP`

**O que faz:** Preenche automaticamente com a data/hora atual do servidor se nenhum valor for fornecido.

**Por que usar?** Elimina a necessidade de passar `created_at` e `updated_at` em cada INSERT/UPDATE.

### Índices

```sql
CREATE UNIQUE INDEX IF NOT EXISTS users_username_lower_unique
    ON users (LOWER(username));

CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_unique
    ON users (LOWER(email));
```

**`UNIQUE INDEX`:** Garante que não há duplicatas e acelera as consultas.

**`LOWER(username)`:** Índice **funcional** — aplica `LOWER()` antes de indexar. Isso permite buscas case-insensitive rápidas.

**Por que índice funcional em vez de coluna?** A coluna `username` pode ter "Admin" e "admin" — ambos são válidos sem o índice. Com `LOWER()`, "Admin" e "admin" colidem, impedindo duplicatas case-insensitive.

### Tabela `password_reset_tokens`

```sql
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL
        REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### `REFERENCES users (id) ON DELETE CASCADE`

**O que é:** **Foreign Key** com **cascata de exclusão**.

**Por que usar?** Se um usuário for deletado, todos os tokens de reset associados são deletados automaticamente. Sem `CASCADE`, teríamos tokens órfãos.

#### `token_hash TEXT NOT NULL UNIQUE`

**`UNIQUE`:** Garante que não há dois tokens iguais ativos.

**`TEXT`:** Tipo de texto sem limite de tamanho. Adequado para hashes SHA-256 (64 caracteres hexadecimais).

#### `used_at TIMESTAMPTZ` (nullable)

**Por que nullable?** `NULL` significa "ainda não usado". Uma vez usado, recebe `CURRENT_TIMESTAMP`. Esta é a forma mais eficiente de rastrear tokens usados — sem necessidade de um booleano extra.

### Índice Parcial

```sql
CREATE INDEX IF NOT EXISTS password_reset_tokens_pending_expiry_idx
    ON password_reset_tokens (expires_at)
    WHERE used_at IS NULL;
```

**O que é:** **Índice parcial** — indexa apenas as linhas que satisfazem a condição `WHERE`.

**Por que usar?**
- **Menor tamanho:** Indexa apenas tokens pendentes, não os já usados.
- **Consulta otimizada:** Facilita a limpeza periódica de tokens expirados: `DELETE FROM password_reset_tokens WHERE used_at IS NULL AND expires_at < NOW()`.

---

## 10. Resumo de Todas as Anotações

### Spring Boot / Spring Framework

| Anotação | Onde | O que faz | Por que usar |
|---|---|---|---|
| `@SpringBootApplication` | Classe main | Auto-configuração + component scan | Ponto de entrada do app |
| `@Configuration` | Classes config | Marca classe como fonte de beans | Substitui XML do Spring |
| `@Bean` | Métodos config | Registra retorno como bean no Spring | Injeção de dependências |
| `@Value("${...}")` | Campos/parâmetros | Injeta valor de properties | Externaliza configuração |
| `@Transactional` | Métodos controller | Executa método em transação DB | Garante atomicidade |
| `@RestController` | Classe controller | Controller + serialização JSON | API REST sem views |
| `@RequestMapping` | Classe controller | Prefixo de URL comum | Evita repetição de paths |
| `@GetMapping` | Métodos controller | Mapeia requisições GET | Endpoint de leitura |
| `@PostMapping` | Métodos controller | Mapeia requisições POST | Endpoint de escrita |
| `@RequestBody` | Parâmetros método | Deserializa JSON do corpo | Recebe dados do cliente |
| `@ExceptionHandler` | Método handler | Captura exceção específica | Tratamento centralizado |
| `@RestControllerAdvice` | Classe handler | Intercepta exceções globais | Ponto único de erros |

### Spring Security

| Anotação | Onde | O que faz | Por que usar |
|---|---|---|---|
| (Nenhuma anotação direta) | `SecurityConfig` | Configuração programática via `@Bean` | Spring Security moderno usa DSL, não anotações |

> **Nota:** O Spring Security moderno (5.7+) usa configuração programática (`SecurityFilterChain` bean) em vez de anotações como `@EnableWebSecurity` ou `@PreAuthorize`.

### OpenAPI / Swagger

| Anotação | Onde | O que faz | Por que usar |
|---|---|---|---|
| `@Tag` | Classe controller | Agrupa endpoints na documentação | Organização no Swagger UI |
| `@Operation` | Métodos controller | Documenta um endpoint | Descrição e summary |
| `@Parameter` | Parâmetros método | Configura parâmetro na documentação | Esconder parâmetros internos |
| `@SecurityRequirement` | Métodos controller | Indica autenticação necessária | Documenta requisitos de segurança |

### Jakarta Servlet

| Tipo | Onde | O que faz | Por que usar |
|---|---|---|---|
| `HttpServletRequest` | Parâmetros método | Acesso à requisição HTTP | Sessão, headers, atributos |
| `HttpServletResponse` | Parâmetros método | Acesso à resposta HTTP | Cookies, headers de resposta |

> **Nota:** `jakarta.servlet.*` substitui `javax.servlet.*` a partir do Jakarta EE 9+ (Spring Boot 3.x/4.x).

---

## 11. Padrões e Boas Práticas Utilizados

### 11.1. Injeção de Dependências via Construtor

**Padrão:** Todas as dependências são injetadas pelo construtor, não por campos `@Autowired`.

**Vantagens:**
- Objetos imutáveis (`final` fields)
- Fácil teste unitário (construtor接受 mocks)
- Dependências explícitas e visíveis

### 11.2. Records como DTOs

**Padrão:** Todos os DTOs são `record` do Java 17+.

**Vantagens:**
- Imutabilidade garantida
- Sem boilerplate (constructor, getters, equals, hashCode, toString)
- Serialização JSON automática via Jackson

### 11.3. JdbcTemplate em vez de JPA/Hibernate

**Padrão:** SQL direto via `JdbcTemplate` sem entidades JPA.

**Vantagens:**
- Controle total sobre as queries
- Sem overhead de ORM
- Sem "N+1 query problem"
- Código mais simples para um projeto pequeno

**Desvantagens:**
- Migrations de schema manuais
- Mapeamento manual ResultSet → Objeto
- Não escala bem para domínios complexos

### 11.4. Sessão HTTP com Cookies (não JWT)

**Padrão:** Autenticação baseada em sessão com `JSESSIONID` cookie.

**Vantagens:**
- Logout real e imediato
- Proteção nativa contra XSS (HttpOnly)
- Navegador gerencia cookies automaticamente
- Sem necessidade de refresh token

### 11.5. Proteção CSRF

**Padrão:** Token CSRF em cookie acessível via JS, enviado no header `X-XSRF-TOKEN`.

**Fluxo:**
1. Cliente faz `GET /api/auth/csrf` → recebe cookie `XSRF-TOKEN`
2. Cliente faz `POST /api/auth/login` com header `X-XSRF-TOKEN: <valor>`
3. Servidor verifica que o header e o cookie batem

### 11.6. Segurança em Camadas (Defense in Depth)

| Camada | Proteção |
|---|---|
| **Aplicação** | Validação de inputs, sanitização |
| **Banco** | CHECK constraints, UNIQUE indexes |
| **Rede** | CORS restrito, cookies HttpOnly |
| **Autenticação** | BCrypt, CSRF, session fixation prevention |
| **Tokens** | Hash SHA-256, expiração, uso único |

### 11.7. Text Blocks (Java 15+)

**Padrão:** Strings multi-linha com `""" ... """`.

**Onde usado:**
- Queries SQL
- Mensagens de e-mail
- Descrição OpenAPI

**Vantagem:** Código muito mais legível.

### 11.8. Method References (Java 8+)

**Padrão:** `AbstractHttpConfigurer::disable` em vez de `x -> x.disable()`.

**Vantagem:** Sintaxe mais concisa quando o lambda apenas delega a um método existente.

### 11.9. Variáveis de Ambiente com Defaults

**Padrão:** `${VARIAVEL:valor_padrao}` no `application.properties`.

**Vantagem:** Mesma configuração funciona em dev (defaults) e produção (variáveis reais).

### 11.10. Mensagens Genéricas para Segurança

**Padrão:** Mensagens de erro não revelam informações internas.

| Situação | Mensagem |
|---|---|
| Usuário não encontrado | "Usuário ou senha inválidos." |
| Senha errada | "Usuário ou senha inválidos." |
| E-mail não cadastrado (forgot) | "Se o e-mail estiver cadastrado..." |
| E-mail cadastrado (forgot) | "Se o e-mail estiver cadastrado..." |

**Por que?** Previne **enumeration attacks** — onde o atacante descobre usuários válidos analisando as mensagens de erro.

### 11.11. `FOR UPDATE` para Race Conditions

**Padrão:** Trava de linha no banco durante operações críticas.

**Onde usado:** Reset de senha — garante que um token é usado apenas uma vez, mesmo com requisições simultâneas.

### 11.12. Logging sem Dados Sensíveis

**Padrão:** Logs registram apenas informações seguras.

```java
LOGGER.error(
    "SMTP não conseguiu enviar o e-mail de recuperação ({}).",
    exception.getClass().getSimpleName()  // Apenas o nome da exceção
);
```

**Por que?** Evita vazar e-mails, tokens ou stack traces completos nos logs.

---

## Glossário de Termos

| Termo | Definição |
|---|---|
| **Bean** | Objeto gerenciado pelo contêiner Spring (criado, configurado e destruído pelo framework) |
| **DTO** | Data Transfer Object — objeto usado para transferir dados entre camadas ou sistemas |
| **CSRF** | Cross-Site Request Forgery — ataque que força o usuário a executar ações não intencionais |
| **XSS** | Cross-Site Scripting — ataque que injeta JavaScript malicioso em páginas web |
| **BCrypt** | Algoritmo de hashing de senhas com salt automático e custo configurável |
| **SHA-256** | Algoritmo de hash criptográfico que produz 256 bits de saída |
| **JSESSIONID** | Cookie padrão do Servlet Container para rastrear sessões HTTP |
| **HttpOnly** | Flag do cookie que impede acesso via JavaScript |
| **SameSite** | Flag do cookie que controla envio em requisições cross-site |
| **Record** | Tipo de classe Java imutável para transportar dados (Java 16+/17+) |
| **Text Block** | String multi-linha delimitada por `"""` (Java 15+) |
| **Method Reference** | Sintaxe `Classe::metodo` como atalho para lambda (Java 8+) |
| **Foreign Key** | Restrição que referencia a PRIMARY KEY de outra tabela |
| **ON DELETE CASCADE** | Regra que deleta registros filhos quando o pai é deletado |
| **Índice Parcial** | Índice que cobre apenas um subconjunto das linhas (com WHERE) |
| **Race Condition** | Bug que ocorre quando o resultado depende da ordem de execução de threads concorrentes |
| **Session Fixation** | Ataque onde o atacante fixa uma sessão conhecida antes do login da vítima |
| **User Enumeration** | Ataque que descobre usuários válidos testando entradas e analisando respostas |

---

*Guia gerado para o projeto Store Land — Backend de Autenticação.*
*Spring Boot 4.1.1 | Java 27 | PostgreSQL | Spring Security*