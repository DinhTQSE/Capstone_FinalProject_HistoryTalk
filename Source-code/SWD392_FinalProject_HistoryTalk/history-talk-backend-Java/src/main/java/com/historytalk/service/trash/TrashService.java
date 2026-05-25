package com.historytalk.service.trash;

import com.historytalk.dto.trash.BulkTrashActionResponse;
import com.historytalk.dto.trash.TrashItemResponse;

import java.util.List;

public interface TrashService {

    List<TrashItemResponse> getDeletedCharacters();

    List<TrashItemResponse> getDeletedContexts();

    List<TrashItemResponse> getDeletedQuizzes();

    BulkTrashActionResponse restoreCharacters(List<String> ids);

    BulkTrashActionResponse restoreContexts(List<String> ids);

    BulkTrashActionResponse restoreQuizzes(List<String> ids);

    BulkTrashActionResponse hardDeleteCharacters(List<String> ids);

    BulkTrashActionResponse hardDeleteContexts(List<String> ids);

    BulkTrashActionResponse hardDeleteQuizzes(List<String> ids);
}
