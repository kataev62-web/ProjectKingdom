#!/usr/bin/env python3
"""Offline voice3 asset preparation. Requires ffmpeg/ffprobe, never runs in-game."""
import hashlib
import json
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[3]
ASSETS = ROOT / 'src/main/resources/assets/kingdomnpc'
MASTERS = ROOT / 'design/npc_voice/voice3-masters'
CREATIONS = {
    'male_1': ('LwMZ7aWswO', 132), 'male_2': ('YMKvDJzWeC', 1355),
    'male_3': ('4RhMZ1H9Aa', 130), 'female_1': ('TdxEXNuVNR', 849),
    'female_2': ('YMKvDyYWeC', 856), 'female_3': ('TdxEXiFVNR', 1101),
}


def main():
    sounds_path = ASSETS / 'sounds.json'
    durations_path = ASSETS / 'voice/recordings.properties'
    sounds = json.loads(sounds_path.read_text(encoding='utf-8'))
    durations = dict(line.split('=', 1) for line in durations_path.read_text().splitlines()
                     if line and not line.startswith('#'))
    filters = ('silenceremove=start_periods=1:start_duration=0.02:start_threshold=-48dB,'
               'areverse,silenceremove=start_periods=1:start_duration=0.02:start_threshold=-48dB,'
               'areverse,loudnorm=I=-18:TP=-1.5:LRA=7,alimiter=limit=0.7:level=false,'
               'afade=t=in:d=0.004,areverse,afade=t=in:d=0.006,areverse')
    manifest = {'text': 'Как проходит твой день?', 'model': 'eleven_turbo_v2_5',
                'stability': 0.65, 'similarity_boost': 0.8, 'speed': 0.95,
                'use_speaker_boost': True, 'recordings': []}
    for bank, (creation, voice_id) in CREATIONS.items():
        source = MASTERS / (bank + '.mp3')
        target = ASSETS / 'sounds/voice' / bank / 'how_day.ogg'
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(source), '-af', filters,
                        '-ac', '1', '-ar', '24000', '-c:a', 'libvorbis', '-q:a', '3', str(target)], check=True)
        probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_format',
                                                   '-show_streams', '-of', 'json', str(target)]))
        ms = round(float(probe['format']['duration']) * 1000)
        assert 500 <= ms <= 6000, (bank, ms)
        assert probe['streams'][0]['channels'] == 1
        assert probe['streams'][0]['codec_name'] == 'vorbis'
        durations[bank + '.how_day'] = str(ms)
        sounds['voice.' + bank + '.how_day'] = {
            'sounds': [{'name': 'kingdomnpc:voice/' + bank + '/how_day', 'stream': False, 'preload': True}]}
        manifest['recordings'].append({'bank': bank, 'creation': creation, 'voice_id': voice_id,
                                      'duration_ms': ms, 'ogg_sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                                      'master_sha256': hashlib.sha256(source.read_bytes()).hexdigest()})
    sounds_path.write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    durations_path.write_text(''.join(key + '=' + durations[key] + '\n' for key in sorted(durations)), encoding='ascii')
    (MASTERS / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'recordings': len(manifest['recordings']),
                      'durations_ms': [r['duration_ms'] for r in manifest['recordings']]}))


if __name__ == '__main__':
    main()
