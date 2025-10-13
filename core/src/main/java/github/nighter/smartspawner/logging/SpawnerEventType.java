package github.nighter.smartspawner.logging;

/**
 * Defines all loggable spawner events in the SmartSpawner plugin.
 * Each event type represents a specific action or interaction with spawners.
 */
public enum SpawnerEventType {
    // Spawner lifecycle events
    SPAWNER_PLACE("Spawner placed"),
    SPAWNER_BREAK("Spawner broken"),
    SPAWNER_EXPLODE("Spawner destroyed by explosion"),
    
    // Spawner stacking events
    SPAWNER_STACK_HAND("Spawner stacked by hand"),
    SPAWNER_STACK_GUI("Spawner stacked via GUI"),
    SPAWNER_DESTACK_GUI("Spawner destacked via GUI"),
    
    // GUI interaction events
    SPAWNER_GUI_OPEN("Spawner GUI opened"),
    SPAWNER_STORAGE_OPEN("Storage GUI opened"),
    SPAWNER_STACKER_OPEN("Stacker GUI opened"),
    
    // Player actions
    SPAWNER_EXP_CLAIM("Experience claimed"),
    SPAWNER_SELL_ALL("Items sold"),
    SPAWNER_SELL_AND_CLAIM("Items sold and exp claimed"),
    
    // Storage actions
    SPAWNER_ITEM_TAKE_ALL("All items taken from storage"),
    SPAWNER_ITEM_DROP("Item dropped from storage"),
    SPAWNER_ITEMS_SORT("Items sorted in storage"),
    
    // Command events
    // Note: These events capture ALL command executions including admin actions like /ss give
    // The full_command metadata contains all command parameters (target player, amount, entity type, etc.)
    COMMAND_EXECUTE_PLAYER("Command executed by player"),
    COMMAND_EXECUTE_CONSOLE("Command executed by console"),
    COMMAND_EXECUTE_RCON("Command executed by RCON"),
    
    // Entity type change
    SPAWNER_EGG_CHANGE("Spawner entity type changed");
    
    private final String description;
    
    SpawnerEventType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
