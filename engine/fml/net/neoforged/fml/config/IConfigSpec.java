package net.neoforged.fml.config;
import com.electronwill.nightconfig.core.*;
public interface IConfigSpec {
    boolean isEmpty();
    void validateSpec(ModConfig config);
    boolean isCorrect(UnmodifiableCommentedConfig config);
    void correct(CommentedConfig config);
    void acceptConfig(ILoadedConfig config);
    interface ILoadedConfig { CommentedConfig config(); void save(); }
}
