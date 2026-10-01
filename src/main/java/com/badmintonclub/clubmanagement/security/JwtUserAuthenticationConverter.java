package com.badmintonclub.clubmanagement.security;

import com.badmintonclub.clubmanagement.enums.UserStatus;
import com.badmintonclub.clubmanagement.repository.UserAuthView;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chuyển JWT hợp lệ thành Authentication, có đối chiếu DB ở mỗi request:
 * - Tài khoản bị khóa / ngừng hoạt động / không còn tồn tại -> 401 ngay, không chờ token hết hạn.
 * - Quyền lấy theo vai trò HIỆN TẠI trong DB (không dùng claim "roles" trong token),
 *   nên đổi vai trò có hiệu lực ngay ở request tiếp theo.
 * Ném AuthenticationException -> BearerTokenAuthenticationFilter chuyển cho entry point trong SecurityConfig.
 */
@Component
@RequiredArgsConstructor
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UserAuthView user = userRepository.findAuthViewById(CurrentUser.id(jwt))
                .orElseThrow(() -> new InvalidBearerTokenException("Tài khoản không còn tồn tại"));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new LockedException("Tài khoản đã bị khóa");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new DisabledException("Tài khoản đã ngừng hoạt động");
        }

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRoleName()));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
