package cpw.mods.modlauncher.api;
public interface ITransformerVotingContext {
    String getClassName();
    boolean doesClassExist();
    default String getReason() { return "classloading"; }
}
