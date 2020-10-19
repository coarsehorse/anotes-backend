package com.anotes.service;

import com.anotes.controller.request.BackupRequest;
import com.anotes.entity.Note;
import com.anotes.entity.Snapshot;
import com.anotes.entity.User;
import com.anotes.repository.NoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NoteServiceImpl extends BaseServiceImpl<Note, NoteRepository> implements NoteService {

    private final SnapshotService snapshotService;

    @Autowired
    protected NoteServiceImpl(
            NoteRepository repository,
            SnapshotService snapshotService
    ) {
        super(repository);
        this.snapshotService = snapshotService;
    }

    @Override
    @Transactional
    public Snapshot backup(BackupRequest request, User user) {
        // Check snapshot
        String notesMd5 = DigestUtils.md5DigestAsHex(request.toString().getBytes());
        return snapshotService.findByMd5(notesMd5)
                .peek(snapshot ->
                        log.info("Backup with such md5 hash is already exists, snapshotMd5={}", snapshot.getMd5())
                )
                .getOrElse(() -> {
                    // Create Snapshot
                    Snapshot snapshot = new Snapshot(notesMd5);
                    Snapshot savedSnapshot = snapshotService.save(snapshot);

                    // Backup notes
                    List<Note> notesToSave = request.getNotes()
                            .stream()
                            .map(reqNote ->
                                    new Note(
                                            user,
                                            savedSnapshot,
                                            reqNote.getTitle(),
                                            reqNote.getText(),
                                            reqNote.getPinned(),
                                            reqNote.getReminderDate()
                                    )
                            )
                            .collect(Collectors.toList());
                    List<Note> savedNotes = getRepository().saveAll(notesToSave);
                    log.info("Notes backed up successfully, userId={}, notes={}", user.getId(), savedNotes);

                    return savedSnapshot;
                });
    }
}
