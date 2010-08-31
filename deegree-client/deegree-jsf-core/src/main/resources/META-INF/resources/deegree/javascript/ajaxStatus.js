function processAjaxUpdate(msgId, modal) {
	function processEvent(data) {
		var msg = document.getElementById(msgId);
		if (msg != null) {
			if (data.status == "begin") {
				if (modal) {
					document.body.style.cursor = 'wait'
				}
				msg.style.display = '';
			} else if (data.status == "success") {
				if (modal) {
					document.body.style.cursor = 'auto'
				}
				msg.style.display = 'none';
			}
		}
	}
	return processEvent;
};

function registerAjaxStatus(msgId, modal) {
	jsf.ajax.addOnEvent(processAjaxUpdate(msgId, modal));
}
