package controllers;

import com.openseedbox.mvc.controllers.Base;
import java.io.IOException;

public class Payment extends Base {
	
	public static void paymentReturn() throws IOException {
		/*
		Map<String, String> p = Util.getUrlParameters(request.querystring);
		String token = p.get("token");
		Invoice i = Invoice.getByPayPalToken(token);
		if (i != null) {
			i.paymentDateUtc = new Date();
			i.save();
			User u = getCurrentUser();
			u.paidForPlan = true;
			u.save();
			PlanSwitch.notifyUser(u, true);
			setGeneralMessage("Payment succeeded!");
		} else {
			setGeneralErrorMessage("Paypal token mismatch!");
		}
		render("payment/finish.html");*/
	}
	
	public static void paymentCancel() {
		setGeneralErrorMessage("Payment cancelled!");
		render("payment/finish.html");
	}
	
}
