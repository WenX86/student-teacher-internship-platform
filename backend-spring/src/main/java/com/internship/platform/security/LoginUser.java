package com.internship.platform.security;

public record LoginUser(
        String id,
        String account,
        String name,
        String role,
        String collegeId
) {
}
