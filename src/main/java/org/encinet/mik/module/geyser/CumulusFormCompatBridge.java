package org.encinet.mik.module.geyser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The one intentional reflective boundary in the Geyser integration.
 *
 * <p>Paper can expose Geyser's API class to this plugin while resolving the
 * API's transitive Cumulus dependency in a different plugin class loader. A
 * direct invocation of {@code sendForm(UUID, Form)} then fails before the call
 * with a JVM loader-constraint violation. This bridge constructs the form and
 * resolves that single method from Geyser's own Cumulus type identity. No
 * Cumulus object escapes this class.</p>
 */
final class CumulusFormCompatBridge {

    private static final String CUMULUS_FORM = "org.geysermc.cumulus.form.Form";
    private static final String SIMPLE_FORM = "org.geysermc.cumulus.form.SimpleForm";
    private static final String SIMPLE_RESPONSE =
            "org.geysermc.cumulus.response.SimpleFormResponse";

    private final Object api;
    private final Method sendForm;
    private final Method formBuilder;
    private final Method title;
    private final Method content;
    private final Method button;
    private final Method closedOrInvalidResultHandler;
    private final Method validResultHandler;
    private final Method build;
    private final Method clickedButtonId;

    private CumulusFormCompatBridge(Object api, Class<?> apiContract)
            throws ReflectiveOperationException {
        this.api = Objects.requireNonNull(api, "api");
        Objects.requireNonNull(apiContract, "apiContract");
        if (!apiContract.isInstance(api)) {
            throw new IllegalArgumentException("Geyser API instance does not match its contract");
        }

        sendForm = findMethod(apiContract, "sendForm", method -> {
            Class<?>[] parameters = method.getParameterTypes();
            return parameters.length == 2
                    && parameters[0] == UUID.class
                    && parameters[1].getName().equals(CUMULUS_FORM);
        });

        Class<?> formType = sendForm.getParameterTypes()[1];
        ClassLoader cumulusLoader = formType.getClassLoader();
        if (cumulusLoader == null) {
            throw new ClassNotFoundException("Cumulus Form has no defining class loader");
        }
        Class<?> simpleFormType = Class.forName(SIMPLE_FORM, true, cumulusLoader);
        if (!formType.isAssignableFrom(simpleFormType)) {
            throw new LinkageError("Geyser resolved incompatible Cumulus Form classes");
        }
        Class<?> responseType = Class.forName(SIMPLE_RESPONSE, true, cumulusLoader);

        formBuilder = simpleFormType.getMethod("builder");
        Class<?> builderType = formBuilder.getReturnType();
        title = builderType.getMethod("title", String.class);
        content = builderType.getMethod("content", String.class);
        button = builderType.getMethod("button", String.class);
        closedOrInvalidResultHandler = builderType.getMethod(
                "closedOrInvalidResultHandler", Runnable.class);
        validResultHandler = builderType.getMethod("validResultHandler", Consumer.class);
        build = builderType.getMethod("build");
        clickedButtonId = responseType.getMethod("clickedButtonId");
    }

    static CumulusFormCompatBridge create(Object api, Class<?> apiContract)
            throws ReflectiveOperationException {
        return new CumulusFormCompatBridge(api, apiContract);
    }

    boolean sendForm(
            UUID playerId,
            BedrockSimpleForm form,
            Consumer<Throwable> callbackFailure
    ) throws ReflectiveOperationException {
        Object builder = invoke(formBuilder, null);
        invoke(title, builder, form.title());
        invoke(content, builder, form.content());
        invoke(closedOrInvalidResultHandler, builder, form.closed());
        Consumer<Object> responseHandler = response -> {
            try {
                int selected = ((Number) invoke(clickedButtonId, response)).intValue();
                form.selected().accept(selected);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                callbackFailure.accept(error);
                form.closed().run();
            }
        };
        invoke(validResultHandler, builder, responseHandler);
        for (String label : form.buttons()) {
            invoke(button, builder, label);
        }
        Object geyserOwnedForm = invoke(build, builder);
        return Boolean.TRUE.equals(invoke(sendForm, api, playerId, geyserOwnedForm));
    }

    private static Method findMethod(
            Class<?> type,
            String name,
            java.util.function.Predicate<Method> predicate
    ) throws NoSuchMethodException {
        return Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(name))
                .filter(predicate)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(type.getName() + '#' + name));
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error linkage) {
                throw linkage;
            }
            throw new ReflectiveOperationException(cause);
        }
    }
}
