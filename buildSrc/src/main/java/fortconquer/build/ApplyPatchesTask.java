package fortconquer.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import javax.inject.Inject;

/**
 * Copies the pristine apktool decode and applies every patch listed in {@code patches/series}
 * (in order, strictly). Lines starting with '#' and blank lines in the series file are ignored.
 */
public abstract class ApplyPatchesTask extends DefaultTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDecodedDir();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getPatchesDir();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Inject
    protected abstract FileSystemOperations getFs();

    @TaskAction
    public void run() throws IOException {
        Path out = getOutputDir().get().getAsFile().toPath().toAbsolutePath().normalize();
        getFs().sync(spec -> {
            spec.from(getDecodedDir());
            spec.into(out.toFile());
        });
        Path patches = getPatchesDir().get().getAsFile().toPath();
        Path series = patches.resolve("series");
        List<String> names = new ArrayList<>();
        for (String line : Files.readAllLines(series, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) names.add(t);
        }
        for (String name : names) {
            Path p = patches.resolve(name);
            if (!Files.isRegularFile(p)) throw new IllegalStateException("patches/series lists missing patch " + name);
            List<String> touched = UnifiedDiff.apply(p, out);
            getLogger().lifecycle("applied {} ({} file(s))", name, touched.size());
        }
    }
}
