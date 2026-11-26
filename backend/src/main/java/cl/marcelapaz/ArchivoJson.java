package cl.marcelapaz;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

// Persiste la colección de cada servicio en un archivo JSON independiente.
final class ArchivoJson<T> {
    private final Path path;
    private final Class<T[]> arrayType;
    private final ObjectMapper mapper;

    ArchivoJson(Path path, Class<T[]> arrayType, ObjectMapper mapper) {
        this.path = path;
        this.arrayType = arrayType;
        this.mapper = mapper;
    }

    // Devuelve una lista vacía hasta que se guarde el primer registro.
    List<T> leer() {
        try {
            return Files.exists(path)
                ? List.of(mapper.readValue(Files.readAllBytes(path), arrayType))
                : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Escribe en un archivo temporal y lo reemplaza atómicamente para conservar un JSON completo.
    void guardar(List<T> values) {
        try {
            Files.createDirectories(path.getParent());
            Path temporal = Files.createTempFile(path.getParent(), "datos-", ".tmp");
            try {
                Files.write(temporal, mapper.writeValueAsBytes(values));
                Files.move(temporal, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporal);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

