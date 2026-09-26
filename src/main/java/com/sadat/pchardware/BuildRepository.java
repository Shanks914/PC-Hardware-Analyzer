package com.sadat.pchardware;

import java.sql.SQLException;
import java.util.List;

public interface BuildRepository {
    long save(Long existingId, String name, List<Part> parts) throws SQLException;

    List<SavedBuild> findAll() throws SQLException;

    SavedBuild findById(long id) throws SQLException;

    void delete(long id) throws SQLException;
}
