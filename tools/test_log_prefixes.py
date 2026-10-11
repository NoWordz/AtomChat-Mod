import unittest
from verify_log_prefixes import violations
class LogPrefixGuardTest(unittest.TestCase):
    def test_missing_and_dynamic_unwrapped_messages_fail(self):
        for call in ['AtomChat.LOGGER.info("old",failure);','LOG.warning(makeMessage());','logger.debug("old");']:
            self.assertTrue(violations(call),call)
    def test_bad_chain_duplicate_and_unowned_outlets_fail(self):
        for call in ['System.out.println("bad");','failure.printStackTrace();',
                     'LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("", "bad"));',
                     'LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("notify", "[AtomChat][AtomChat] bad"));']:
            self.assertTrue(violations(call),call)
    def test_dynamic_template_and_throwable_arguments_survive(self):
        self.assertFalse(violations('AtomChat.LOGGER.error(com.atom.chat.diagnostics.AtomChatLog.message("resource", template()),failure);'))
    def test_repeated_real_chain_prefix_fails(self):
        self.assertTrue(violations('LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("notify", "[AtomChat][notify] [AtomChat][notify] bad"));'))
if __name__=='__main__': unittest.main()
