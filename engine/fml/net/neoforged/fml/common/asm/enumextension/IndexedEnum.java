package net.neoforged.fml.common.asm.enumextension;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
public @interface IndexedEnum { int value() default 0; }
