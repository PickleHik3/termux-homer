package com.termux.privileged;

/**
 * Enumeration of privileged operations that can be performed through the backend
 */
public enum PrivilegedOperation {
    GET_INSTALLED_PACKAGES("get_installed_packages"),
    INSTALL_PACKAGE("install_package"),
    UNINSTALL_PACKAGE("uninstall_package"),
    SET_COMPONENT_ENABLED("set_component_enabled"),
    EXECUTE_COMMAND("execute_command"),
    SYSTEM_SETTINGS_MODIFY("system_settings_modify"),
    FILE_SYSTEM_OPERATIONS("file_system_operations"),
    SERVICE_CONTROL("service_control"),
    PROCESS_MANAGEMENT("process_management");
    
    private final String operationKey;
    
    PrivilegedOperation(String operationKey) {
        this.operationKey = operationKey;
    }
    
    public String getOperationKey() {
        return operationKey;
    }
    
    /**
     * Find operation by key
     * @param key The operation key
     * @return The corresponding PrivilegedOperation or null if not found
     */
    public static PrivilegedOperation fromKey(String key) {
        for (PrivilegedOperation operation : values()) {
            if (operation.getOperationKey().equals(key)) {
                return operation;
            }
        }
        return null;
    }
}