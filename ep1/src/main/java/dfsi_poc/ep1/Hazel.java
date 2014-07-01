package dfsi_poc.ep1;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Hazel {

	@SuppressWarnings("resource")
	public static void main(String[] args) {
		new ClassPathXmlApplicationContext(
				"hazel_applicationContext.xml");
	}

}
