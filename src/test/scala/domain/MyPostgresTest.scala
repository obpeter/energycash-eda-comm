package at.energydash
package domain


import com.typesafe.slick.testkit.util.StandardTestDBs.Postgres
import com.typesafe.slick.testkit.util.{ExternalJdbcTestDB, ProfileTest, TestDB, Testkit}

class MyPostgresTest extends ProfileTest(Postgres)