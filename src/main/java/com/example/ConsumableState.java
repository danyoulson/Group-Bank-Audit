package com.example;

import java.util.List;

public interface ConsumableState {
    Object get();
    List<ItemContainerItem> getItems();

    String whoOwnsThis();
}
