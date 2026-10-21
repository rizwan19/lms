package com.saasresearcher.learning.identity.config;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.ErrorCodes;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Supplies the authenticated {@link Actor} to controller methods. */
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Actor.class);
    }

    @Override
    public Actor resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object actor = webRequest.getAttribute(ActiveAccountFilter.ACTOR_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, "Sign in required");
        }
        return (Actor) actor;
    }
}
