package net.fabricmc.api;

import java.lang.annotation.*;

@Retention(RetentionPolicy.CLASS)
@Repeatable(EnvironmentInterfaces.class)
@Target(ElementType.TYPE)
@Documented
public @interface EnvironmentInterface { EnvType value(); Class<?> itf(); }
