package dev.abstratium.abstrauth.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.interceptor.InterceptorBinding;

/**
 * Annotation to verify that the authenticated account is a member of
 * the organization specified in the JWT orgId claim.
 * 
 * This prevents forged JWT orgId claims from accessing data in organizations
 * where the account is not a member.
 */
@InterceptorBinding
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface VerifyOrgMembership {
}
