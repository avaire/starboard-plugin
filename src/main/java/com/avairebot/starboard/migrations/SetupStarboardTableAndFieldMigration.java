package com.avairebot.starboard.migrations;

import com.avairebot.Constants;
import com.avairebot.contracts.database.migrations.Migration;
import com.avairebot.database.schema.Schema;
import com.avairebot.starboard.Starboard;

import java.sql.SQLException;

public class SetupStarboardTableAndFieldMigration implements Migration {

    @Override
    public String created_at() {
        return "Thu, May 10, 2018 8:44 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        schema.createIfNotExists(Starboard.STARBOARD_TABLE, table -> {
            table.String("message_id", 32);
            table.String("original_id", 32);
        });

        if (!schema.hasColumn(Constants.GUILD_TABLE_NAME, "starboard")) {
            schema.getDbm().queryUpdate(String.format(
                    "ALTER TABLE `%s` ADD `starboard` VARCHAR(64) NULL DEFAULT NULL;",
                    Constants.GUILD_TABLE_NAME
            ));
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        schema.dropIfExists(Starboard.STARBOARD_TABLE);

        if (schema.hasColumn(Constants.GUILD_TABLE_NAME, "starboard")) {
            schema.getDbm().queryUpdate(String.format(
                    "ALTER TABLE `%s` DROP `starboard`;",
                    Constants.GUILD_TABLE_NAME
            ));
        }

        return true;
    }
}
