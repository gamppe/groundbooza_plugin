package com.example.maincore;

/** Global on/off switch for the land-entity and pet combat protections (not block protection). */
public class PvpProtectionState {

    private boolean enabled = true; // protected by default

    public boolean isEnabled() {
        return enabled;
    }

    public boolean toggle() {
        enabled = !enabled;
        return enabled;
    }
}
