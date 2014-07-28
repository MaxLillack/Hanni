
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import models.Clone;
import models.Koopa;

import org.junit.*;

import play.twirl.api.Html;
import views.html.index;


/**
*
* Simple (JUnit) tests that can call all parts of a play app.
* If you are interested in mocking a whole application, see the wiki for more details.
*
*/
public class ApplicationTest {

	@Test
	public void test1() throws Exception
	{
		Clone clone = new Clone();
	}
	

	@Test
	public void test2() throws Exception
	{
		Clone clone = new Clone();
		clone.getCobolStatistics();
		clone.potentialType4Clones();
	}
	
	@Test
	public void testKoopa() throws Exception
	{
		Config conf = ConfigFactory.load();
		String[] args = {
				conf.getString("cobolPath"),
				conf.getString("cobolASTPath")
		};
		Koopa.parse(args);
	}

}
