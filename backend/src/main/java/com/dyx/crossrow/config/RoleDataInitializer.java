package com.dyx.crossrow.config;

import com.dyx.crossrow.model.Role;
import com.dyx.crossrow.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleDataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        initializeRoles();
    }

    private void initializeRoles() {
        if (roleRepository.findByName("USER").isEmpty()) {
            Role userRole = Role.builder()
                    .name("USER")
                    .displayName("普通用户")
                    .description("普通用户，有基础的 API 调用配额")
                    .dailyChatLimit(100)
                    .dailyAgentLimit(5)
                    .build();
            roleRepository.save(userRole);
            log.info("初始化角色: USER");
        }

        if (roleRepository.findByName("ADMIN").isEmpty()) {
            Role adminRole = Role.builder()
                    .name("ADMIN")
                    .displayName("管理员")
                    .description("管理员，无限制的 API 调用配额")
                    .dailyChatLimit(-1)  // -1 表示无限制
                    .dailyAgentLimit(-1)
                    .build();
            roleRepository.save(adminRole);
            log.info("初始化角色: ADMIN");
        }
    }
}
