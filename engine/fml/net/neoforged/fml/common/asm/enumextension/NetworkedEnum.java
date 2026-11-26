package net.neoforged.fml.common.asm.enumextension;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
public @interface NetworkedEnum {
    NetworkCheck value();
    enum NetworkCheck { CLIENTBOUND, SERVERBOUND, BIDIRECTIONAL }
}
