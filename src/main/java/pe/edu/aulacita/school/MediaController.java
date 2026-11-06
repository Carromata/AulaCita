package pe.edu.aulacita.school;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class MediaController {
    private final SchoolService school;

    public MediaController(SchoolService school) {
        this.school = school;
    }

    @GetMapping("/media/institucion/{clave}")
    public ResponseEntity<Resource> institutionImage(@PathVariable String clave) {
        var path = school.resolveStoredImage(clave);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentType(MediaType.parseMediaType(school.imageContentType(clave)))
                .body(resource);
    }
}
