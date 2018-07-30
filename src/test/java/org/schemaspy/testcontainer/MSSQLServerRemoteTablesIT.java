/*
 * Copyright (C) 2018 Nils Petzaell
 *
 * This file is part of SchemaSpy.
 *
 * SchemaSpy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SchemaSpy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SchemaSpy. If not, see <http://www.gnu.org/licenses/>.
 */
package org.schemaspy.testcontainer;

import com.github.npetzall.testcontainers.junit.jdbc.JdbcContainerRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.schemaspy.Config;
import org.schemaspy.cli.CommandLineArgumentParser;
import org.schemaspy.cli.CommandLineArguments;
import org.schemaspy.model.Database;
import org.schemaspy.model.ProgressListener;
import org.schemaspy.service.DatabaseService;
import org.schemaspy.service.SqlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.MSSQLServerContainer;

import javax.script.ScriptException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static com.github.npetzall.testcontainers.junit.jdbc.JdbcAssumptions.assumeDriverIsPresent;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Rafal Kasa
 * @author Nils Petzaell
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext
public class MSSQLServerRemoteTablesIT {
    @Autowired
    private SqlService sqlService;

    @Autowired
    private DatabaseService databaseService;

    @Mock
    private ProgressListener progressListener;

    @Autowired
    private CommandLineArgumentParser commandLineArgumentParser;

    private static Database database;

    @ClassRule
    public static JdbcContainerRule<MSSQLServerContainer> jdbcContainerRule =
            new JdbcContainerRule<>(() -> new MSSQLServerContainer())
                    .assumeDockerIsPresent()
                    .withAssumptions(assumeDriverIsPresent())
                    .withInitScript("integrationTesting/dbScripts/mssql_remote_tables.sql");

    @Before
    public synchronized void gatheringSchemaDetailsTest() throws SQLException, IOException, ScriptException, URISyntaxException {
        if (database == null) {
            createDatabaseRepresentation();
        }
    }

    private void createDatabaseRepresentation() throws SQLException, IOException, URISyntaxException {
        String[] args = {
                "-t", "mssql08",
                "-db", "ACME",
                "-o", "target/integrationtesting/mssql",
                "-u", "schemaspy",
                "-p", "qwerty123!",
                "-host", jdbcContainerRule.getContainer().getContainerIpAddress(),
                "-port", jdbcContainerRule.getContainer().getMappedPort(1433).toString()
        };
        CommandLineArguments arguments = commandLineArgumentParser.parse(args);
        Config config = new Config(args);
        DatabaseMetaData databaseMetaData = sqlService.connect(config);
        Database database = new Database(
                sqlService.getDbmsMeta(),
                arguments.getDatabaseName(),
                databaseMetaData.getConnection().getCatalog(),
                databaseMetaData.getConnection().getSchema()
        );
        databaseService.gatheringSchemaDetails(config, database, null, progressListener);
        this.database = database;
    }

    @Test
    public void databaseShouldHaveZeroRemoteTables() {
        assertThat(database.getRemoteTables()).isEmpty();
    }
}