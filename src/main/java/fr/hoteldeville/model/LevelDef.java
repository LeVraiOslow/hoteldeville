package fr.hoteldeville.model;

import java.util.List;

/** Coût et durée pour atteindre un niveau d'hôtel de ville. */
public record LevelDef(int level, long timeSeconds, List<Requirement> requirements) {
}
