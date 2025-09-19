package org.caureq.caureqopsboard.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.IngestDTO;
import org.caureq.caureqopsboard.service.IngestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestController {
    private final IngestService ingestService;
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ingest(@Valid @RequestBody IngestDTO body) {
        ingestService.ingest(body);
    }
}
