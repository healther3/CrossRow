package com.dyx.crossrow.service;

import cn.hutool.crypto.digest.BCrypt;
import com.dyx.crossrow.model.User;
import com.dyx.crossrow.model.dto.AuthRequest;
import com.dyx.crossrow.model.dto.AuthResponse;
import com.dyx.crossrow.repository.UserRepository;
import com.dyx.crossrow.utils.JwtUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class AuthService {
    @Resource
    private JwtUtils jwtUtils;
    @Resource
    private UserRepository userRepository;

    /**
     *  User register
     */
    public void register(AuthRequest request) {
        Optional<User> existingUser = userRepository.findByUsername(request.getUsername());
        if (existingUser.isPresent()) {
            throw new RuntimeException("existed username");
        }
        // hash
        String hashedPassword = BCrypt.hashpw(request.getPassword(), BCrypt.gensalt());

        // put data into db
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(hashedPassword);
        userRepository.save(user);
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("username not found"));
        if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("username or password error");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());

        return new AuthResponse(token, user.getId(), user.getUsername());
    }

}
