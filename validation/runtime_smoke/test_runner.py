import contextlib
import hashlib
import io
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch
import run

class FailClosedTests(unittest.TestCase):
    def test_existing_world_is_not_touched(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);world=root/'level.dat';world.write_bytes(b'KEEP')
            with self.assertRaises(ValueError):run.claim_root(root)
            self.assertEqual(b'KEEP',world.read_bytes())
            self.assertFalse((root/'.kingdom-smoke').exists())
    def test_wrong_cached_checksum_does_not_replace_file(self):
        with tempfile.TemporaryDirectory() as directory:
            jar=Path(directory)/'mod.jar';jar.write_bytes(b'original')
            with patch.object(run,'open_url') as request:
                with self.assertRaises(ValueError):run.download('https://example.com',jar,'0'*64,'sha256')
                request.assert_not_called()
            self.assertEqual(b'original',jar.read_bytes())
    def test_download_mismatch_is_not_installed(self):
        with tempfile.TemporaryDirectory() as directory:
            jar=Path(directory)/'mod.jar'
            with patch.object(run,'open_url',return_value=io.BytesIO(b'bad')):
                with self.assertRaises(ValueError):run.download('https://example.com',jar,'0'*64,'sha256')
            self.assertFalse(jar.exists())
    def test_valid_download_is_verified_before_install(self):
        with tempfile.TemporaryDirectory() as directory:
            jar=Path(directory)/'mod.jar'
            with patch.object(run,'open_url',return_value=io.BytesIO(b'good')):
                run.download('https://example.com',jar,hashlib.sha512(b'good').hexdigest(),'sha512')
            self.assertEqual(b'good',jar.read_bytes())
    def test_no_eula_means_no_files_or_process(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)/'server'
            argv=['run.py','--root',str(root),'--jar','unused.jar','--sha256','0'*64,'--commit','a'*40]
            with patch('sys.argv',argv),patch.object(run.subprocess,'run',return_value=types.SimpleNamespace(stderr='openjdk version "17.0.17"',stdout='')),patch.object(run.subprocess,'Popen') as launch,contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):run.main()
                launch.assert_not_called()
            self.assertFalse(root.exists())

if __name__=='__main__':unittest.main()
