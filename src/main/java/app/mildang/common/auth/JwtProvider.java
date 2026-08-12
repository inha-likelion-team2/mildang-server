package app.mildang.common.auth;

import app.mildang.common.config.MildangProps;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtProvider(MildangProps props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = props.jwt().accessTtlSeconds();
    }

    public String createAccessToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofSeconds(accessTtlSeconds))))
                .signWith(key)
                .compact();
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    /** @return userId. 만료는 TOKEN_EXPIRED, 그 외 위변조는 TOKEN_INVALID. */
    public String parseUserId(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject();
        } catch (ExpiredJwtException e) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
    }
}
