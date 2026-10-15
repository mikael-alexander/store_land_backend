package br.com.store_land.controller;

import br.com.store_land.dto.AuthResponse;
import br.com.store_land.dto.ForgotPasswordRequest;
import br.com.store_land.dto.LoginRequest;
import br.com.store_land.dto.MessageResponse;
import br.com.store_land.dto.RegisterRequest;
import br.com.store_land.dto.ResetPasswordRequest;
import br.com.store_land.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Cadastro, login e recuperação de acesso.")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String GENERIC_RESET_MESSAGE =
            "Se o e-mail estiver cadastrado, enviaremos instruções para redefinir sua senha.";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String mailFrom;

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
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.mailFrom = mailFrom;
    }

    @GetMapping("/csrf")
    @Operation(
            summary = "Obter token CSRF",
            description = "Obtém um token CSRF e o cookie XSRF-TOKEN necessários para chamadas POST."
    )
    public CsrfResponse csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken());
    }

    @PostMapping("/register")
    @Transactional
    @Operation(summary = "Criar conta", description = "Cadastra o usuário e inicia uma sessão.")
    @SecurityRequirement(name = "csrfToken")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String fullName = requiredText(request.fullName(), "Nome completo");
        String username = requiredText(request.username(), "Usuário");
        String email = requiredText(request.email(), "E-mail").toLowerCase(Locale.ROOT);
        String password = validatePassword(request.password());
        String confirmPassword = requiredText(request.confirmPassword(), "Confirmação da senha");

        if (!password.equals(confirmPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "As senhas não coincidem."
            );
        }

        if (fullName.length() > 150 || username.length() > 50 || email.length() > 254) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Um ou mais campos excedem o tamanho máximo permitido."
            );
        }
        if (!username.matches("[A-Za-z0-9_.-]{3,50}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O usuário deve ter de 3 a 50 caracteres: letras, números, ponto, hífen ou sublinhado."
            );
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail inválido.");
        }

        try {
            jdbcTemplate.update("""
                            INSERT INTO users (full_name, username, email, password_hash)
                            VALUES (?, ?, ?, ?)
                            """,
                    fullName, username, email, passwordEncoder.encode(password)
            );
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Este usuário ou e-mail já está cadastrado."
            );
        }

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password)
        );
        saveAuthentication(authentication, servletRequest, servletResponse);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(findUser(authentication.getName())));
    }

    @PostMapping("/login")
    @Operation(summary = "Entrar", description = "Autentica por usuário ou e-mail e inicia uma sessão.")
    @SecurityRequirement(name = "csrfToken")
    public AuthResponse login(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String username = requiredText(request.username(), "Usuário");
        String password = requiredText(request.password(), "Senha");
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password)
        );
        saveAuthentication(authentication, servletRequest, servletResponse);
        return new AuthResponse(findUser(authentication.getName()));
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar sessão", description = "Retorna os dados do usuário autenticado.")
    @SecurityRequirement(name = "sessionCookie")
    public AuthResponse currentUser(Authentication authentication) {
        return new AuthResponse(findUser(authentication.getName()));
    }

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

    @PostMapping("/forgot-password")
    @Transactional
    @Operation(
            summary = "Solicitar redefinição de senha",
            description = "Envia instruções por e-mail quando o endereço estiver cadastrado."
    )
    @SecurityRequirement(name = "csrfToken")
    public MessageResponse forgotPassword(@RequestBody ForgotPasswordRequest request) {
        String email = requiredText(request.email(), "E-mail").toLowerCase(Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail inválido.");
        }

        var users = jdbcTemplate.query("""
                        SELECT id, email
                        FROM users
                        WHERE LOWER(email) = LOWER(?) AND is_active = TRUE
                        """,
                (resultSet, rowNumber) -> new ResetRecipient(
                        resultSet.getLong("id"),
                        resultSet.getString("email")
                ),
                email
        );

        if (!users.isEmpty()) {
            ResetRecipient recipient = users.get(0);
            byte[] randomToken = new byte[32];
            SECURE_RANDOM.nextBytes(randomToken);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
            String tokenHash = sha256(token);
            Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);

            jdbcTemplate.update("""
                            INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                            VALUES (?, ?, ?)
                            """,
                    recipient.id(), tokenHash, Timestamp.from(expiresAt)
            );

            String resetUrl = frontendUrl + "/redefinir-senha?token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(recipient.email());
            message.setSubject("Redefinição de senha - Store Land");
            message.setText("""
                    Recebemos uma solicitação para redefinir a senha da sua conta.
                    Use o link abaixo em até 30 minutos:

                    %s

                    Se você não solicitou a redefinição, ignore esta mensagem.
                    """.formatted(resetUrl));
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
        }

        return new MessageResponse(GENERIC_RESET_MESSAGE);
    }

    @PostMapping("/reset-password")
    @Transactional
    @Operation(summary = "Redefinir senha", description = "Redefine a senha usando um token válido.")
    @SecurityRequirement(name = "csrfToken")
    public MessageResponse resetPassword(@RequestBody ResetPasswordRequest request) {
        String token = requiredText(request.token(), "Token");
        String password = validatePassword(request.password());
        String tokenHash = sha256(token);

        var tokens = jdbcTemplate.query("""
                        SELECT user_id
                        FROM password_reset_tokens
                        WHERE token_hash = ?
                          AND expires_at > CURRENT_TIMESTAMP
                          AND used_at IS NULL
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> new ResetToken(
                        resultSet.getLong("user_id")
                ),
                tokenHash
        );

        if (tokens.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O link de redefinição é inválido ou expirou."
            );
        }

        ResetToken resetToken = tokens.get(0);
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
        return new MessageResponse("Senha redefinida. Você já pode entrar com a nova senha.");
    }

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

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " é obrigatório."
            );
        }
        return value.trim();
    }

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

    private record ResetRecipient(long id, String email) {}

    private record ResetToken(long userId) {}

    public record CsrfResponse(String token) {}
}
