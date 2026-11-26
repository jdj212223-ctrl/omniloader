package net.neoforged.fml.common.asm.enumextension;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
public @interface NamedEnum { int value() default 0; }
