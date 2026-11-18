package com.historytalk.dto.map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request body for {@code PATCH /map-focuses/reorder}.
 * <p>
 * The client posts an ordered list of focus UUIDs.
 * The server assigns {@code order_index = 0, 1, 2, …} based on the
 * position of each ID in this list.
 * <p>
 * All provided IDs must belong to the same context and must not be deleted.
 * Any focus omitted from the list retains its current order_index unchanged.
 */
@Data
public class ReorderMapFocusRequest {

    @NotNull(message = "focusIds không được để trống")
    @Size(min = 1, message = "Cần cung cấp ít nhất một focusId")
    private List<String> focusIds;
}
