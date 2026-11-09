package it.uniroma3.sii.service.storage;

import java.nio.file.Files;
import java.nio.file.Path;

/** Identificativi di storage: un solo nome, mai un percorso fornito dal client. */
final class StorageId {

    private StorageId() {
    }

    static String validate(String id) {
        if (id == null || id.isBlank() || id.equals(".") || id.equals("..")
                || id.contains("/") || id.contains("\\") || id.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Identificativo di storage non valido.");
        }
        return id;
    }

    static Path resolve(Path parent, String id) {
        Path child = parent.resolve(validate(id));
        if (Files.isSymbolicLink(child)) {
            throw new IllegalArgumentException("L'identificativo di storage non può essere un collegamento simbolico.");
        }
        return child;
    }
}
