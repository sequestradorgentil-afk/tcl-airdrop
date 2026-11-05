import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
STUB='''#!/usr/bin/env bash
printf '%s\\n' "$*" >> "$FAKE_TRACE"
case "$*" in
 connect*) echo connected ;;
 *get-state*) if [ "${FAKE_UNAUTHORIZED:-}" = 1 ]; then exit 7; fi; echo device ;;
 *ro.product.model*) echo "${FAKE_MODEL:-BeyondTV2}" ;;
 *ro.build.version.sdk*) echo 28 ;;
 *ro.product.cpu.abilist*) echo armeabi-v7a,armeabi ;;
 *getprop*) echo test ;;
 *'install -r'*) exit "${FAKE_INSTALL_EXIT:-0}" ;;
 *'am start'*) echo 'Status: ok' ;;
esac
'''
class MacHelpers(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='cinema air ')
        self.root=Path(self.tmp.name);self.bundle=self.root/'bundle'
        shutil.copytree(ROOT/'scripts',self.bundle/'scripts')
        adb=self.root/'Downloads/platform-tools/adb';adb.parent.mkdir(parents=True)
        adb.write_text(STUB);adb.chmod(0o755)
        self.trace=self.root/'adb.trace'
        self.env={**os.environ,'HOME':str(self.root),'FAKE_TRACE':str(self.trace)}
        apk=self.bundle/'CinemaAir-experimental-armv7.apk';apk.write_bytes(b'test fixture, not an APK')
        (self.bundle/'APK-SHA256SUMS').write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+'  '+apk.name+'\n')
    def tearDown(self): self.tmp.cleanup()
    def run_helper(self,name,**variables):
        return subprocess.run(['bash',str(self.bundle/'scripts'/name)],env={**self.env,**variables},capture_output=True,text=True)
    def commands(self): return self.trace.read_text() if self.trace.exists() else ''
    def test_install_and_space_destination(self):
        r=self.run_helper('install-tv.sh');self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        trace=self.commands();self.assertIn('install -r',trace)
        self.assertIn('com.gabriel.cinemaair/.MainActivity',trace)
        self.assertNotIn('uninstall',trace);self.assertNotIn('settings put',trace)
        self.assertTrue(list((self.root/'Documents/TV LAUNCHER/logs').glob('*.log')))
    def test_bad_checksum_never_calls_adb(self):
        (self.bundle/'CinemaAir-experimental-armv7.apk').write_bytes(b'altered')
        self.assertNotEqual(self.run_helper('install-tv.sh').returncode,0)
        self.assertEqual(self.commands(),'')
    def test_wrong_tv_never_installs(self):
        self.assertNotEqual(self.run_helper('install-tv.sh',FAKE_MODEL='OtherTV').returncode,0)
        self.assertNotIn('install -r',self.commands())
    def test_unauthorized_never_installs(self):
        self.assertNotEqual(self.run_helper('install-tv.sh',FAKE_UNAUTHORIZED='1').returncode,0)
        self.assertNotIn('install -r',self.commands())
    def test_failed_install_preserves_status_and_does_not_launch(self):
        self.assertEqual(self.run_helper('install-tv.sh',FAKE_INSTALL_EXIT='17').returncode,17)
        self.assertNotIn('am start',self.commands());self.assertNotIn('uninstall',self.commands())
    def test_stop_only_receiver_and_returns_cinema_home(self):
        self.assertEqual(self.run_helper('stop-tv.sh').returncode,0)
        self.assertIn('am force-stop com.gabriel.cinemaair',self.commands())
        self.assertIn('com.gabriel.cinemahome/.MainActivity',self.commands())
        self.assertNotIn('uninstall',self.commands())
if __name__=='__main__': unittest.main()
