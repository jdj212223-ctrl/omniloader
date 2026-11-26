package net.neoforged.fml;
import java.util.List; import java.util.stream.Collectors;
public class ModLoadingException extends RuntimeException {
    private final List<ModLoadingIssue> issues;
    public ModLoadingException(ModLoadingIssue issue) { this(List.of(issue)); }
    public ModLoadingException(List<ModLoadingIssue> issues) { super(null, issues.stream().map(ModLoadingIssue::cause).filter(java.util.Objects::nonNull).findFirst().orElse(null)); this.issues = issues; }
    public List<ModLoadingIssue> getIssues() { return issues; }
    @Override public String getMessage() { return "Loading errors encountered:\n" + issues.stream().map(i -> "\t- " + i).collect(Collectors.joining("\n")); }
}
