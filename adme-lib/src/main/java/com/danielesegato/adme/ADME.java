package com.danielesegato.adme;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.danielesegato.adme.config.ADMEConfigUtils;
import com.danielesegato.adme.config.ADMEEntityConfig;
import com.danielesegato.adme.config.ADMEFieldConfig;
import com.danielesegato.adme.config.ADMEIndexConstraintConfig;
import com.danielesegato.adme.config.OnForeignUpdateDelete;
import com.danielesegato.adme.db.ADMESerializer;
import com.danielesegato.adme.db.ADMESerializerMapping;
import com.danielesegato.adme.utils.SQLStringHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Android Database Made Easy library entry point.
 * <p/>
 * Utility methods to create and drop the entities in the database.
 */
public class ADME {

    private static final String LOGTAG = InternalADMEConsts.LOGTAG;

    /**
     * Convert an entity row into it's Andoird {@link ContentValues} ready to use in an insert or update
     * query.
     * <p/>
     * If the class of the entityRow is not annotated with {@link com.danielesegato.adme.annotation.ADMEEntity}
     * this method will throw a RuntimeException.
     *
     * @param values             The ContentValues to recycle or null (one will be created), it will not
     *                           be cleared, it's the caller job to do so if you require it.
     * @param entityRow          The instance of the entity from which you want to extract values.
     * @param includeId          <em>True</em> if you want to include the ID in the content values,
     *                           <em>false</em> otherwise, if the id is autogenerated it will never be
     *                           included, regardless of the value you pass here
     * @param includeForeignKeys <em>True</em> to include foreign key, <em>false</em> otherwise (useful
     *                           if you need to use back references)
     * @return the ContentValues from the entity row
     */
    public static <T> ContentValues entityToContentValues(ContentValues values, T entityRow, boolean includeId, boolean includeForeignKeys) {
        Set<String> columns = getAllColumnsSet(entityRow.getClass(), includeId, includeForeignKeys);
        return entityToContentValues(values, entityRow, columns);
    }

    /**
     * Build a set with all the columns of of an entity
     *
     * @param entityClass        the entity class
     * @param <T>                the type of object
     * @param includeId          <em>True</em> if you want to include the ID in the content values,
     *                           <em>false</em> otherwise, if the id is autogenerated it will never be
     *                           included, regardless of the value you pass here
     * @param includeForeignKeys <em>True</em> to include foreign key, <em>false</em> otherwise (useful
     *                           if you need to use back references)
     * @return the complete set of columns for this entity
     */
    public static <T> Set<String> getAllColumnsSet(Class<T> entityClass, boolean includeId, boolean includeForeignKeys) {
        final ADMEEntityConfig<T> entityConfig = ADMEConfigUtils.lookupADMEEntityConfig(entityClass);
        Set<String> columns = new HashSet<String>();
        for (ADMEFieldConfig field : entityConfig.getFieldsConfig()) {
            if (!includeId && field.isGeneratedId()) {
                continue;
            } else if (!includeId && field.isId()) {
                continue;
            }
            if (!includeForeignKeys && field.isForeign()) {
                continue;
            }
            columns.add(field.getColumnName());
        }
        return columns;
    }

    /**
     * Convert an entity row into it's Andoird {@link ContentValues} ready to use in an insert or update
     * query.
     * <p/>
     * If the class of the entityRow is not annotated with {@link com.danielesegato.adme.annotation.ADMEEntity}
     * this method will throw a RuntimeException.
     *
     * @param values    The ContentValues to recycle or null (one will be created), it will not
     *                  be cleared, it's the caller job to do so if you require it.
     * @param entityRow The instance of the entity from which you want to extract values.
     * @param columns   The set of columns to include in the content values.
     * @return the ContentValues from the entity row
     */
    public static <T> ContentValues entityToContentValues(ContentValues values, T entityRow, Set<String> columns) {
        final ADMEEntityConfig<T> entityConfig = ADMEConfigUtils.lookupADMEEntityConfig((Class<T>) entityRow.getClass());
        if (values == null) {
            values = new ContentValues();
        }
        try {
            for (final ADMEFieldConfig fieldConfig : entityConfig.getFieldsConfig()) {
                if (!columns.contains(fieldConfig.getColumnName())) {
                    continue;
                }
                final Field field;
                final Object instance;
                if (!fieldConfig.isForeign()) {
                    field = fieldConfig.getJavaField();
                    instance = entityRow;
                } else {
                    field = fieldConfig.getForeignFieldConfig().getJavaField();
                    instance = fieldConfig.getJavaField().get(entityRow);
                }
                // TODO use get method if annotated like that?
                final Object fieldValue = instance != null ? field.get(instance) : null;
                final ADMESerializer admeSerializer = fieldConfig.getADMESerializer();
                admeSerializer.storeInContentValues(fieldConfig.getColumnName(), values, fieldValue, fieldConfig);
            }
        } catch (IllegalAccessException e) {
            String msg = String.format("Error serializing entity %s, couldn't access some field, class: %s", entityConfig.getEntityName(), entityRow);
            Log.e(LOGTAG, msg, e);
            throw new RuntimeException(msg, e);
        }
        return values;
    }

    /**
     * Convert a {@link android.database.Cursor} into an Entity instance.
     * <p/>
     * If the class of the entityRow is not annotated with {@link com.danielesegato.adme.annotation.ADMEEntity}
     * this method will throw a RuntimeException.
     * <p/>
     * Any missing column will be ignored.
     *
     * @param cursor  The Cursor containing the data, it will not
     *                be cleared, it's the caller job to do so if you require it.
     * @param clazz   The class of the instance to be created, it must have a public empty constructor
     * @param <T>     the type of entity
     * @return the Entity with the data extracted by the cursor
     */
    private static <T> T cursorToEntity(Cursor cursor, Class<T> clazz) {
        return cursorToEntity(cursor, clazz, getAllColumnsSet(clazz, true, true));
    }

    /**
     * Convert a {@link android.database.Cursor} into an Entity instance.
     * <p/>
     * If the class of the entityRow is not annotated with {@link com.danielesegato.adme.annotation.ADMEEntity}
     * this method will throw a RuntimeException.
     * <p/>
     * Any missing column will be ignored.
     *
     * @param cursor  The Cursor containing the data, it will not
     *                be cleared, it's the caller job to do so if you require it.
     * @param entity  an instance of the entity, fields will be overridden)
     * @param <T>     the type of entity
     * @return the Entity with the data extracted by the cursor
     */
    public static <T> T cursorToEntity(Cursor cursor, T entity) {
        return cursorToEntity(cursor, entity, getAllColumnsSet(entity.getClass(), true, true));
    }

    /**
     * Convert a {@link android.database.Cursor} into an Entity instance. Only the given set of columns will be read from the cursor.
     * <p/>
     * If the class of the entityRow is not annotated with {@link com.danielesegato.adme.annotation.ADMEEntity}
     * this method will throw a RuntimeException.
     * <p/>
     * Any missing column will be ignored.
     *
     * @param cursor  The Cursor containing the data, it will not
     *                be cleared, it's the caller job to do so if you require it.
     * @param clazz   The class of the instance to be created, it must have a public empty constructor
     * @param columns The set of columns to extract from the Cursor, any missing column will be ignored.
     * @param <T>     the type of entity
     * @return the Entity with the data extracted by the cursor
     */
    private static <T> T cursorToEntity(Cursor cursor, Class<T> clazz, Set<String> columns) {
        try {
            T entity = clazz.newInstance();
            return cursorToEntity(cursor, entity, columns);
        } catch (InstantiationException e) {
            throw new RuntimeException(String.format("the instance for class %s cannot be created", clazz.getName()), e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(String.format("the default constructor for class %s is not visible", clazz.getName()), e);
        }
    }

    /**
     * Convert a {@link android.database.Cursor} into an Entity instance. Only the given set of columns will be read from the cursor.
     * <p/>
     * If the class of the entityRow is not annotated with {@link com.danielesegato.adme.annotation.ADMEEntity}
     * this method will throw a RuntimeException.
     * <p/>
     * Any missing column will be ignored.
     *
     * @param cursor  The Cursor containing the data, it will not
     *                be cleared, it's the caller job to do so if you require it.
     * @param entity  an instance of the entity, fields will be overridden)
     * @param columns The set of columns to extract from the Cursor, any missing column will be ignored.
     * @param <T>     the type of entity
     * @return the Entity with the data extracted by the cursor
     */
    public static <T> T cursorToEntity(Cursor cursor, T entity, Set<String> columns) {
        final ADMEEntityConfig<T> entityConfig = ADMEConfigUtils.lookupADMEEntityConfig((Class<T>)entity.getClass());
        try {
            for (final ADMEFieldConfig fieldConfig : entityConfig.getFieldsConfig()) {
                if (!columns.contains(fieldConfig.getColumnName())) {
                    continue;
                }
                final Field field;
                Object instance;
                if (!fieldConfig.isForeign()) {
                    field = fieldConfig.getJavaField();
                    instance = entity;
                } else {
                    field = fieldConfig.getForeignFieldConfig().getJavaField();
                    instance = fieldConfig.getJavaField().get(entity);
                    if (instance == null) {
                        try {
                            instance = fieldConfig.getJavaField().getType().newInstance();
                        } catch (InstantiationException e) {
                            throw new RuntimeException(String.format("the instance for class %s of foreign field %s in entity %s cannot be created",
                                    fieldConfig.getJavaField().getType().getName(), fieldConfig.getJavaField().getName(), entityConfig.getClass().getName()), e);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(String.format("the default constructor for class %s of foreign field %s in entity %s is not visible",
                                    fieldConfig.getJavaField().getType().getName(), fieldConfig.getJavaField().getName(), entityConfig.getClass().getName()), e);
                        }
                        fieldConfig.getJavaField().setAccessible(true);
                        fieldConfig.getJavaField().set(entity, instance);
                    }
                }
                // TODO use set method if annotated like that?
                final ADMESerializer admeSerializer = fieldConfig.getADMESerializer();
                int columnIndex = cursor.getColumnIndex(fieldConfig.getColumnName());
                if (columnIndex >= 0) {
                    final Object fieldValue = admeSerializer.sqlToJava(cursor, columnIndex, fieldConfig);
                    field.setAccessible(true);
                    field.set(instance, fieldValue);
                }
            }
        } catch (IllegalAccessException e) {
            String msg = String.format("Error serializing entity %s, couldn't access some field, class: %s", entityConfig.getEntityName(), entity);
            Log.e(LOGTAG, msg, e);
            throw new RuntimeException(msg, e);
        }
        return entity;
    }

    /**
     * Create the table for the entity of the entityClass. The entityClass must be annotated with
     * an {@link com.danielesegato.adme.annotation.ADMEEntity} annotation.
     * <p/>
     * This method will parse the {@link com.danielesegato.adme.config.ADMEEntityConfig} of the given
     * class if needed, then cache it (it will also parse and cache the config for every foreign class).
     * <p/>
     * Every issue in parsing the entity configuration will raise an exception.
     * <p/>
     * Every index for the table is also created.
     * <p/>
     * If the table or one index already exist an exception will be raised.
     *
     * @param db          the database in which the table should be created.
     * @param entityClass the {@link com.danielesegato.adme.annotation.ADMEEntity} annotated class.
     * @param <T>         the type of the class
     */
    public static <T> void createTable(final SQLiteDatabase db, final Class<T> entityClass) {
        ADMEEntityConfig<T> entityConfig = ADMEConfigUtils.lookupADMEEntityConfig(entityClass);
        List<String> statements = getCreateTableStatements(entityConfig);
        for (String statement : statements) {
            Log.d(LOGTAG, String.format("Creating table for %s, executing statement: %s", entityClass.getSimpleName(), statement));
            db.execSQL(statement);
        }
        Log.i(LOGTAG, String.format("Table for %s created SUCCESSFULLY", entityClass.getName()));
    }

    /**
     * Same as {@link #createTable(android.database.sqlite.SQLiteDatabase, Class)} but it will not
     * execute the table and indexes statements, it will return them.
     *
     * @param dbEntityConfig the {@link com.danielesegato.adme.annotation.ADMEEntity} annotated class.
     * @return the list of SQLite statements to create this table and its indexes in the database.
     */
    public static List<String> getCreateTableStatements(final ADMEEntityConfig<?> dbEntityConfig) {
        // http://www.sqlite.org/lang_createtable.html
        final List<String> statements = new ArrayList<String>();
        final StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ");
        SQLStringHelper.appendEscapedEntityOrField(sb, dbEntityConfig.getEntityName());
        sb.append(" (");
        appendColumnsDefinitions(sb, dbEntityConfig);
        appendEntityConstraints(sb, dbEntityConfig);
        sb.append(") ");
        statements.add(sb.toString());

        for (final ADMEIndexConstraintConfig indexConstraintConfig : dbEntityConfig.getIndexConstraintConfigList()) {
            sb.setLength(0);
            appendIndexStatement(sb, indexConstraintConfig);
            statements.add(sb.toString());
        }
        return statements;
    }

    public static <T> void dropTable(final SQLiteDatabase db, final Class<T> entityClass) {
        ADMEEntityConfig<T> entityConfig = ADMEConfigUtils.lookupADMEEntityConfig(entityClass);
        dropTable(db, entityConfig.getEntityName());
    }

    public static void dropTable(final SQLiteDatabase db, final String tableName) {
        List<String> statements = getDropTableStatements(db, tableName);
        for (String statement : statements) {
            Log.d(LOGTAG, String.format("Dropping table for %s, executing statement: %s", tableName, statement));
            db.execSQL(statement);
        }
        Log.i(LOGTAG, String.format("Table for %s dropped SUCCESSFULLY", tableName));
    }

    public static List<String> getDropTableStatements(final SQLiteDatabase db, final String tableName) {
        return getDropTableStatements(tableName, getTableIndexNameSet(db, tableName));
    }

    private static <T> List<String> getDropTableStatements(final String tableName, final Set<String> indexNameSet) {
        // http://www.sqlite.org/lang_droptable.html
        final List<String> statements = new ArrayList<String>();
        final StringBuilder sb = new StringBuilder();
        for (final String indexName : indexNameSet) {
            sb.append("DROP INDEX IF EXISTS ");
            SQLStringHelper.appendEscapedEntityOrField(sb, indexName);
            statements.add(sb.toString());
            sb.setLength(0);
        }
        sb.append("DROP TABLE IF EXISTS ");
        SQLStringHelper.appendEscapedEntityOrField(sb, tableName);
        statements.add(sb.toString());
        return statements;
    }

    /**
     * Query the database to obtain the set of index name associated to a given table.
     *
     * @param db        the database in to query
     * @param tableName the table name
     * @return the set of index name
     */
    public static Set<String> getTableIndexNameSet(final SQLiteDatabase db, final String tableName) {
        Set<String> indexNameSet = new HashSet<String>();
        Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type == ? AND tbl_name == ?", new String[]{"index", tableName});
        while (c.moveToNext()) {
            final String indexName = c.getString(0);
            if (!indexName.startsWith("sqlite_autoindex_")) {
                indexNameSet.add(c.getString(0));
            }
        }
        c.close();
        return indexNameSet;
    }

    private static StringBuilder appendColumnsDefinitions(final StringBuilder sb, final ADMEEntityConfig<?> dbEntityConfig) {
        boolean first = true;
        for (ADMEFieldConfig fieldConfig : dbEntityConfig.getFieldsConfig()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            appendColumnDefinition(sb, fieldConfig);
        }
        return sb;
    }

    private static void appendColumnDefinition(final StringBuilder sb, final ADMEFieldConfig fieldConfig) {
        SQLStringHelper.appendEscapedEntityOrField(sb, fieldConfig.getColumnName()).append(' ');

        ADMESerializer admeSerializer = fieldConfig.getADMESerializer();
        switch (admeSerializer.getSQLiteType()) {
            case INTEGER:
                sb.append("INTEGER");
                break;
            case TEXT:
                sb.append("TEXT");
                break;
            case REAL:
                sb.append("REAL");
                break;
            case NUMERIC:
                sb.append("NUMERIC");
                break;
            case NONE:
                sb.append("NONE");
                break;
            default:
                throw new UnsupportedOperationException(String.format(
                        "SQLiteType %s Unknown or not supported for field %s in entity %s",
                        admeSerializer.getSQLiteType(), fieldConfig.getColumnName(), fieldConfig.getADMEEntityConfig().getEntityName()
                ));
        }
        sb.append(' ');

        if (fieldConfig.isId()) {
            sb.append("PRIMARY KEY ");
            if (fieldConfig.isGeneratedId()) {
                sb.append(" AUTOINCREMENT ");
            }
        } else {
            if (fieldConfig.isNullable()) {
                sb.append("NULL ");
            } else {
                sb.append("NON NULL ");
            }
            if (fieldConfig.getIndexConstraint() != null && fieldConfig.getIndexConstraint().isUnique()) {
                sb.append("UNIQUE ");
            }
            if (fieldConfig.getDefaultValue() != null) {
                sb.append("DEFAULT ");
                sb.append(admeSerializer.stringToSqlRaw(fieldConfig.getDefaultValue(), fieldConfig));
            }
        }
    }

    private static StringBuilder appendEntityConstraints(final StringBuilder sb, final ADMEEntityConfig<?> dbEntityConfig) {
        for (final ADMEIndexConstraintConfig indexConstraintConfig : dbEntityConfig.getIndexConstraintConfigList()) {
            if (!indexConstraintConfig.isSingleField()) {
                sb.append(", UNIQUE (");
                boolean first = true;
                for (ADMEFieldConfig fieldConfig : indexConstraintConfig.getFields()) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(", ");
                    }
                    SQLStringHelper.appendEscapedEntityOrField(sb, fieldConfig.getColumnName());
                }
                sb.append(")");
            }
        }
        for (final ADMEFieldConfig fieldConfig : dbEntityConfig.getFieldsConfig()) {
            if (fieldConfig.isForeign()) {
                final ADMEFieldConfig foreignFieldConfig = fieldConfig.getForeignFieldConfig();
                sb.append(", FOREIGN KEY (");
                SQLStringHelper.appendEscapedEntityOrField(sb, fieldConfig.getColumnName());
                sb.append(") REFERENCES ");
                SQLStringHelper.appendEscapedEntityOrField(sb, foreignFieldConfig.getADMEEntityConfig().getEntityName());
                if (fieldConfig.getForeignOnDelete() != OnForeignUpdateDelete.NO_ACTION) {
                    sb.append("ON DELETE ").append(fieldConfig.getForeignOnDelete().sql()).append(' ');
                }
                if (fieldConfig.getForeignOnUpdate() != OnForeignUpdateDelete.NO_ACTION) {
                    sb.append("ON UPDATE ").append(fieldConfig.getForeignOnUpdate().sql()).append(' ');
                }
            }
        }
        return sb;
    }

    private static StringBuilder appendIndexStatement(final StringBuilder sb, final ADMEIndexConstraintConfig indexConstraintConfig) {
        // http://www.sqlite.org/lang_createindex.html
        sb.append("CREATE ");
        if (indexConstraintConfig.isUnique()) {
            sb.append("UNIQUE ");
        }
        sb.append("INDEX ");
        SQLStringHelper.appendEscapedEntityOrField(sb, indexConstraintConfig.getIndexName());
        sb.append(" ON ");
        SQLStringHelper.appendEscapedEntityOrField(sb, indexConstraintConfig.getADMEEntityConfig().getEntityName());
        sb.append(" (");
        boolean first = true;
        for (final ADMEFieldConfig fieldConfig : indexConstraintConfig.getFields()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            SQLStringHelper.appendEscapedEntityOrField(sb, fieldConfig.getColumnName());
        }
        sb.append(") ");
        return sb;
    }

    /**
     * Register a custom {@link com.danielesegato.adme.db.ADMESerializer} for a {@link java.lang.Class}.
     * Registering a serializer makes the use of {@link com.danielesegato.adme.annotation.ADMEField} annotation
     * on the custom class possible.
     * <p/>
     * You can also register a custom serializer for the data types already handled by the system, your
     * serializer will override the default one.
     *
     * @param clazz      the custom class type
     * @param serializer the custom serializer
     */
    public static void registerADMESerializer(final Class<?> clazz, final ADMESerializer serializer) {
        ADMESerializerMapping.registerSerializer(clazz, serializer);
    }

    /**
     * Unregister any custom {@link com.danielesegato.adme.db.ADMESerializer} associated to the given
     * {@link java.lang.Class}.
     *
     * @param clazz the custom class type
     */
    public static void unregisterADMESerializer(final Class<?> clazz) {
        ADMESerializerMapping.unregisterSerializer(clazz);
    }
}
