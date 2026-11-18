"""Actual local candidate archive consumption; no network or installed game files."""
import hashlib,json,subprocess,sys,tempfile,unittest,zipfile
from pathlib import Path

SCRIPT=Path(__file__).with_name('fetch_atomlib_input.py')

class CandidateInputTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        (self.root/'tools').mkdir();(self.root/'tools/fetch_atomlib_input.py').write_bytes(SCRIPT.read_bytes())
        self.payload=b'real isolated candidate bytes';self.entry='com/atom/lib/atomlib-test/0.6.0/atomlib-test-0.6.0.jar'
        (self.root/'versions').mkdir();(self.root/'versions/atomlib.json').write_text(json.dumps({'version':'0.6.0','input_archive':{'source':'unreleased-candidate'},'targets':{'test':{'artifact':'atomlib-test'}},'sha256':{'test':hashlib.sha256(self.payload).hexdigest()}}))
        self.archive=self.root/'candidate.zip';self.output=self.root/'result/path.txt'
    def pack(self,entries=None):
        with zipfile.ZipFile(self.archive,'w') as z:
            for name,value in (entries or [(self.entry,self.payload)]):z.writestr(name,value)
    def run_input(self,sha=None):
        return subprocess.run([sys.executable,str(self.root/'tools/fetch_atomlib_input.py'),'--archive',str(self.archive),'--sha256',sha or hashlib.sha256(self.archive.read_bytes()).hexdigest(),'--output',str(self.output)],capture_output=True,text=True)
    def test_local_candidate_is_verified_and_ready_for_gradle(self):
        self.pack();result=self.run_input();self.assertEqual(0,result.returncode,result.stderr)
        repo=Path(self.output.read_text());self.assertEqual(self.payload,(repo/self.entry).read_bytes())
    def test_wrong_archive_digest_rejects_before_writing(self):
        self.pack();result=self.run_input('0'*64);self.assertNotEqual(0,result.returncode);self.assertIn('SHA-256 mismatch',result.stderr);self.assertFalse(self.output.exists())
    def test_changed_jar_rejects_before_writing(self):
        self.pack([(self.entry,b'changed')]);result=self.run_input();self.assertNotEqual(0,result.returncode);self.assertIn('jar SHA-256 mismatch',result.stderr);self.assertFalse(self.output.exists())
    def test_escaping_archive_rejects_before_writing(self):
        self.pack([(self.entry,self.payload),('../escaped',b'bad')]);result=self.run_input();self.assertNotEqual(0,result.returncode);self.assertIn('unsafe archive path',result.stderr);self.assertFalse((self.root/'escaped').exists())
    def test_existing_maven_wip_is_preserved(self):
        self.pack();repo=self.output.parent/'maven';repo.mkdir(parents=True);sentinel=repo/'wip';sentinel.write_bytes(b'keep')
        result=self.run_input();self.assertNotEqual(0,result.returncode);self.assertIn('already exists',result.stderr);self.assertEqual(b'keep',sentinel.read_bytes())

if __name__=='__main__':unittest.main()
