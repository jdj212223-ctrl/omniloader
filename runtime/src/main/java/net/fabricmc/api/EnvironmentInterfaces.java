package net.fabricmc.api;

import java.lang.annotation.*;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Documented
public @interface EnvironmentInterfaces { EnvironmentInterface[] value(); }
