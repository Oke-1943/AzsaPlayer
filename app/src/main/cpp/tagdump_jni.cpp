// 生タグダンプ（仕様書 3.1）用のJNIブリッジ。
// TagLibが返すフィールド名と値を、加工せずにそのまま列挙して返す。
//
// 戻り値の形式：レコードの並び。1レコードは4フィールド（区分, キー, 添字, 値）。
// 各フィールドは「4バイトのビッグエンディアン長 + UTF-8バイト列」で表す。
// JNIの修正UTF-8を経由しないため、絵文字などの補助文字も正しく渡せる。

#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

#include <fileref.h>
#include <tfile.h>
#include <tpropertymap.h>
#include <tstringlist.h>
#include <tvariant.h>
#include <audioproperties.h>

#include <mpegfile.h>
#include <id3v1tag.h>
#include <id3v2tag.h>
#include <id3v2header.h>
#include <id3v2frame.h>
#include <textidentificationframe.h>
#include <commentsframe.h>
#include <attachedpictureframe.h>

#include <flacfile.h>
#include <flacproperties.h>
#include <xiphcomment.h>
#include <vorbisfile.h>
#include <opusfile.h>
#include <opusproperties.h>
#include <oggflacfile.h>
#include <speexfile.h>

#include <mp4file.h>
#include <mp4tag.h>
#include <mp4item.h>
#include <mp4properties.h>

#include <apefile.h>
#include <apetag.h>
#include <apeitem.h>
#include <apeproperties.h>
#include <wavpackfile.h>
#include <wavpackproperties.h>
#include <mpcfile.h>

#include <wavfile.h>
#include <wavproperties.h>
#include <infotag.h>
#include <aifffile.h>
#include <aiffproperties.h>

#include <dsffile.h>
#include <dsfproperties.h>
#include <dsdifffile.h>
#include <dsdiffproperties.h>

namespace {

class RecordWriter {
public:
    void add(const std::string &section, const std::string &key, int index, const std::string &value) {
        putField(section);
        putField(key);
        putField(index < 0 ? std::string() : std::to_string(index));
        putField(value);
    }

    void add(const std::string &section, const std::string &key, int index, const TagLib::String &value) {
        add(section, key, index, value.to8Bit(true));
    }

    void addList(const std::string &section, const std::string &key, const TagLib::StringList &values) {
        if (values.isEmpty()) {
            add(section, key, -1, std::string("(no value)"));
            return;
        }
        int i = 0;
        for (const auto &v : values) add(section, key, i++, v);
    }

    const std::vector<uint8_t> &bytes() const { return buf_; }

private:
    void putField(const std::string &s) {
        const auto n = static_cast<uint32_t>(s.size());
        buf_.push_back(static_cast<uint8_t>(n >> 24));
        buf_.push_back(static_cast<uint8_t>(n >> 16));
        buf_.push_back(static_cast<uint8_t>(n >> 8));
        buf_.push_back(static_cast<uint8_t>(n));
        buf_.insert(buf_.end(), s.begin(), s.end());
    }

    std::vector<uint8_t> buf_;
};

std::string str(const TagLib::ByteVector &v) { return std::string(v.data(), v.size()); }

void dumpId3v2(RecordWriter &w, const TagLib::ID3v2::Tag *tag) {
    if (!tag) return;
    const auto *h = tag->header();
    const std::string section = "ID3v2.";
    const std::string sec = section + std::to_string(h ? h->majorVersion() : 0) + " frames";
    if (tag->frameList().isEmpty()) {
        w.add(sec, "(no frames)", -1, std::string());
        return;
    }
    for (const auto *frame : tag->frameList()) {
        const std::string id = str(frame->frameID());
        if (const auto *txxx = dynamic_cast<const TagLib::ID3v2::UserTextIdentificationFrame *>(frame)) {
            // fieldList()[0] は説明文、以降が値
            TagLib::StringList fields = txxx->fieldList();
            TagLib::StringList values;
            for (size_t i = 1; i < fields.size(); ++i) values.append(fields[i]);
            w.addList(sec, id + ":" + txxx->description().to8Bit(true), values);
        } else if (const auto *text = dynamic_cast<const TagLib::ID3v2::TextIdentificationFrame *>(frame)) {
            w.addList(sec, id, text->fieldList());
        } else if (const auto *comm = dynamic_cast<const TagLib::ID3v2::CommentsFrame *>(frame)) {
            w.add(sec, id + ":" + comm->description().to8Bit(true) + " [" + str(comm->language()) + "]", 0, comm->text());
        } else if (const auto *apic = dynamic_cast<const TagLib::ID3v2::AttachedPictureFrame *>(frame)) {
            w.add(sec, id, 0, apic->mimeType().to8Bit(true) + " / " + std::to_string(apic->picture().size()) + " bytes / type " +
                                  std::to_string(static_cast<int>(apic->type())));
        } else {
            w.add(sec, id, 0, frame->toString());
        }
    }
}

void dumpId3v1(RecordWriter &w, const TagLib::ID3v1::Tag *tag) {
    if (!tag) return;
    const std::string sec = "ID3v1";
    w.add(sec, "TITLE", 0, tag->title());
    w.add(sec, "ARTIST", 0, tag->artist());
    w.add(sec, "ALBUM", 0, tag->album());
    w.add(sec, "YEAR", 0, std::to_string(tag->year()));
    w.add(sec, "GENRE", 0, tag->genre());
}

void dumpXiph(RecordWriter &w, TagLib::Ogg::XiphComment *tag) {
    if (!tag) return;
    const std::string sec = "Vorbis Comment";
    w.add(sec, "(vendor)", 0, tag->vendorID());
    // fieldListMap は同名フィールドの複数個を一つのキーにまとめた結果
    for (const auto &[key, values] : tag->fieldListMap()) w.addList(sec, key.to8Bit(true), values);
    const auto pictures = tag->pictureList();
    if (!pictures.isEmpty()) w.add(sec, "(METADATA_BLOCK_PICTURE)", -1, std::to_string(pictures.size()) + " picture(s)");
}

void dumpFlacPictures(RecordWriter &w, TagLib::FLAC::File *f) {
    const auto pictures = f->pictureList();
    if (!pictures.isEmpty()) w.add("FLAC", "(PICTURE blocks)", -1, std::to_string(pictures.size()) + " picture(s)");
}

void dumpMp4(RecordWriter &w, const TagLib::MP4::Tag *tag) {
    if (!tag) return;
    const std::string sec = "MP4 atoms";
    using Type = TagLib::MP4::Item::Type;
    for (const auto &[key, item] : tag->itemMap()) {
        const std::string k = key.to8Bit(true);
        switch (item.type()) {
            case Type::StringList: w.addList(sec, k, item.toStringList()); break;
            case Type::Bool: w.add(sec, k, 0, std::string(item.toBool() ? "true" : "false")); break;
            case Type::Int: w.add(sec, k, 0, std::to_string(item.toInt())); break;
            case Type::Byte: w.add(sec, k, 0, std::to_string(item.toByte())); break;
            case Type::UInt: w.add(sec, k, 0, std::to_string(item.toUInt())); break;
            case Type::LongLong: w.add(sec, k, 0, std::to_string(item.toLongLong())); break;
            case Type::IntPair: {
                const auto p = item.toIntPair();
                w.add(sec, k, 0, std::to_string(p.first) + " / " + std::to_string(p.second));
                break;
            }
            case Type::CoverArtList: w.add(sec, k, -1, std::to_string(item.toCoverArtList().size()) + " picture(s)"); break;
            case Type::ByteVectorList: {
                int i = 0;
                for (const auto &bv : item.toByteVectorList())
                    w.add(sec, k, i++, "(binary " + std::to_string(bv.size()) + " bytes)");
                break;
            }
            default: w.add(sec, k, -1, std::string("(no value)")); break;
        }
    }
}

void dumpApe(RecordWriter &w, const TagLib::APE::Tag *tag) {
    if (!tag) return;
    const std::string sec = "APE";
    for (const auto &[key, item] : tag->itemListMap()) {
        const std::string k = key.to8Bit(true);
        if (item.type() == TagLib::APE::Item::Binary)
            w.add(sec, k, -1, "(binary " + std::to_string(item.binaryData().size()) + " bytes)");
        else
            w.addList(sec, k, item.values());
    }
}

void dumpRiffInfo(RecordWriter &w, const TagLib::RIFF::Info::Tag *tag) {
    if (!tag) return;
    for (const auto &[key, value] : tag->fieldListMap()) w.add("RIFF INFO", str(key), 0, value);
}

void dumpAudio(RecordWriter &w, TagLib::File *file) {
    const auto *p = file->audioProperties();
    const std::string sec = "Audio properties";
    if (!p) {
        w.add(sec, "(unavailable)", -1, std::string());
        return;
    }
    w.add(sec, "Length (ms)", 0, std::to_string(p->lengthInMilliseconds()));
    w.add(sec, "Bitrate (kbps)", 0, std::to_string(p->bitrate()));
    w.add(sec, "Sample rate (Hz)", 0, std::to_string(p->sampleRate()));
    w.add(sec, "Channels", 0, std::to_string(p->channels()));

    int bits = -1;
    if (auto *x = dynamic_cast<const TagLib::FLAC::Properties *>(p)) bits = x->bitsPerSample();
    else if (auto *x = dynamic_cast<const TagLib::MP4::Properties *>(p)) {
        bits = x->bitsPerSample();
        w.add(sec, "Codec", 0, std::string(x->codec() == TagLib::MP4::Properties::ALAC ? "ALAC"
                                              : x->codec() == TagLib::MP4::Properties::AAC ? "AAC" : "Unknown"));
    }
    else if (auto *x = dynamic_cast<const TagLib::APE::Properties *>(p)) bits = x->bitsPerSample();
    else if (auto *x = dynamic_cast<const TagLib::WavPack::Properties *>(p)) bits = x->bitsPerSample();
    else if (auto *x = dynamic_cast<const TagLib::RIFF::WAV::Properties *>(p)) bits = x->bitsPerSample();
    else if (auto *x = dynamic_cast<const TagLib::RIFF::AIFF::Properties *>(p)) bits = x->bitsPerSample();
    else if (auto *x = dynamic_cast<const TagLib::DSF::Properties *>(p)) bits = x->bitsPerSample();
    else if (auto *x = dynamic_cast<const TagLib::DSDIFF::Properties *>(p)) bits = x->bitsPerSample();
    else if (auto *x = dynamic_cast<const TagLib::Ogg::Opus::Properties *>(p))
        w.add(sec, "Input sample rate (Hz)", 0, std::to_string(x->inputSampleRate()));
    if (bits > 0) w.add(sec, "Bit depth", 0, std::to_string(bits));
}

std::string formatName(TagLib::File *f) {
    if (dynamic_cast<TagLib::MPEG::File *>(f)) return "MPEG (MP3)";
    if (dynamic_cast<TagLib::FLAC::File *>(f)) return "FLAC";
    if (dynamic_cast<TagLib::Ogg::Vorbis::File *>(f)) return "Ogg Vorbis";
    if (dynamic_cast<TagLib::Ogg::Opus::File *>(f)) return "Opus";
    if (dynamic_cast<TagLib::Ogg::FLAC::File *>(f)) return "Ogg FLAC";
    if (dynamic_cast<TagLib::Ogg::Speex::File *>(f)) return "Speex";
    if (dynamic_cast<TagLib::MP4::File *>(f)) return "MP4";
    if (dynamic_cast<TagLib::APE::File *>(f)) return "APE";
    if (dynamic_cast<TagLib::WavPack::File *>(f)) return "WavPack";
    if (dynamic_cast<TagLib::MPC::File *>(f)) return "Musepack";
    if (dynamic_cast<TagLib::RIFF::WAV::File *>(f)) return "WAV";
    if (dynamic_cast<TagLib::RIFF::AIFF::File *>(f)) return "AIFF";
    if (dynamic_cast<TagLib::DSF::File *>(f)) return "DSF";
    if (dynamic_cast<TagLib::DSDIFF::File *>(f)) return "DSDIFF (DFF)";
    return "Other";
}

void dumpFormatSpecific(RecordWriter &w, TagLib::File *f) {
    if (auto *x = dynamic_cast<TagLib::MPEG::File *>(f)) {
        if (x->hasID3v2Tag()) dumpId3v2(w, x->ID3v2Tag());
        if (x->hasAPETag()) dumpApe(w, x->APETag());
        if (x->hasID3v1Tag()) dumpId3v1(w, x->ID3v1Tag());
    } else if (auto *x = dynamic_cast<TagLib::FLAC::File *>(f)) {
        if (x->hasXiphComment()) dumpXiph(w, x->xiphComment());
        dumpFlacPictures(w, x);
        if (x->hasID3v2Tag()) dumpId3v2(w, x->ID3v2Tag());
        if (x->hasID3v1Tag()) dumpId3v1(w, x->ID3v1Tag());
    } else if (auto *x = dynamic_cast<TagLib::Ogg::File *>(f)) {
        dumpXiph(w, dynamic_cast<TagLib::Ogg::XiphComment *>(x->tag()));
    } else if (auto *x = dynamic_cast<TagLib::MP4::File *>(f)) {
        dumpMp4(w, x->tag());
    } else if (auto *x = dynamic_cast<TagLib::APE::File *>(f)) {
        if (x->hasAPETag()) dumpApe(w, x->APETag());
        if (x->hasID3v1Tag()) dumpId3v1(w, x->ID3v1Tag());
    } else if (auto *x = dynamic_cast<TagLib::WavPack::File *>(f)) {
        if (x->hasAPETag()) dumpApe(w, x->APETag());
        if (x->hasID3v1Tag()) dumpId3v1(w, x->ID3v1Tag());
    } else if (auto *x = dynamic_cast<TagLib::MPC::File *>(f)) {
        if (x->hasAPETag()) dumpApe(w, x->APETag());
    } else if (auto *x = dynamic_cast<TagLib::RIFF::WAV::File *>(f)) {
        if (x->hasID3v2Tag()) dumpId3v2(w, x->ID3v2Tag());
        if (x->hasInfoTag()) dumpRiffInfo(w, x->InfoTag());
    } else if (auto *x = dynamic_cast<TagLib::RIFF::AIFF::File *>(f)) {
        if (x->hasID3v2Tag()) dumpId3v2(w, x->tag());
    } else if (auto *x = dynamic_cast<TagLib::DSF::File *>(f)) {
        dumpId3v2(w, x->tag());
    } else if (auto *x = dynamic_cast<TagLib::DSDIFF::File *>(f)) {
        if (x->hasID3v2Tag()) dumpId3v2(w, x->ID3v2Tag());
    }
}

std::vector<uint8_t> dumpFile(const std::string &path) {
    RecordWriter w;
    TagLib::FileRef ref(path.c_str(), true, TagLib::AudioProperties::Average);
    if (ref.isNull() || !ref.file() || !ref.file()->isValid()) {
        w.add("Error", "Read failed", -1, std::string("TagLib could not open this file"));
        return w.bytes();
    }
    TagLib::File *file = ref.file();
    w.add("Summary", "Format", 0, formatName(file));

    // 一次情報：PropertyMap（NULL区切り・同名フィールドの複数個を展開済み）
    const TagLib::PropertyMap props = file->properties();
    if (props.isEmpty()) w.add("PropertyMap", "(empty)", -1, std::string());
    for (const auto &[key, values] : props) w.addList("PropertyMap", key.to8Bit(true), values);

    // PropertyMapに変換できなかったフィールド
    for (const auto &u : props.unsupportedData()) w.add("PropertyMap (unsupported)", u.to8Bit(true), -1, std::string());

    // 画像などの複合プロパティ
    for (const auto &k : file->complexPropertyKeys()) {
        w.add("Complex properties", k.to8Bit(true), -1, std::to_string(file->complexProperties(k).size()) + " item(s)");
    }

    // 形式固有の生フィールド（PropertyMapへの正規化前の名前を確認するため）
    dumpFormatSpecific(w, file);
    dumpAudio(w, file);
    return w.bytes();
}

int bitsPerSample(const TagLib::AudioProperties *p) {
    if (auto *x = dynamic_cast<const TagLib::FLAC::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<const TagLib::MP4::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<const TagLib::APE::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<const TagLib::WavPack::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<const TagLib::RIFF::WAV::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<const TagLib::RIFF::AIFF::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<const TagLib::DSF::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<const TagLib::DSDIFF::Properties *>(p)) return x->bitsPerSample();
    return 0;
}

std::string codecName(TagLib::File *f) {
    if (auto *x = dynamic_cast<TagLib::MP4::File *>(f)) {
        if (auto *p = x->audioProperties()) {
            if (p->codec() == TagLib::MP4::Properties::ALAC) return "ALAC";
            if (p->codec() == TagLib::MP4::Properties::AAC) return "AAC";
        }
        return "MP4";
    }
    if (dynamic_cast<TagLib::MPEG::File *>(f)) return "MP3";
    if (dynamic_cast<TagLib::FLAC::File *>(f)) return "FLAC";
    if (dynamic_cast<TagLib::Ogg::Vorbis::File *>(f)) return "Vorbis";
    if (dynamic_cast<TagLib::Ogg::Opus::File *>(f)) return "Opus";
    if (dynamic_cast<TagLib::Ogg::FLAC::File *>(f)) return "FLAC";
    if (dynamic_cast<TagLib::APE::File *>(f)) return "APE";
    if (dynamic_cast<TagLib::WavPack::File *>(f)) return "WavPack";
    if (dynamic_cast<TagLib::MPC::File *>(f)) return "Musepack";
    if (dynamic_cast<TagLib::RIFF::WAV::File *>(f)) return "WAV";
    if (dynamic_cast<TagLib::RIFF::AIFF::File *>(f)) return "AIFF";
    if (dynamic_cast<TagLib::DSF::File *>(f)) return "DSF";
    if (dynamic_cast<TagLib::DSDIFF::File *>(f)) return "DFF";
    return "Unknown";
}

/** ライブラリ取り込み用：PropertyMap（区分 "P"）と音声情報（区分 "A"）だけを返す。 */
std::vector<uint8_t> readFile(const std::string &path) {
    RecordWriter w;
    TagLib::FileRef ref(path.c_str(), true, TagLib::AudioProperties::Average);
    if (ref.isNull() || !ref.file() || !ref.file()->isValid()) {
        w.add("E", "error", -1, std::string("open_failed"));
        return w.bytes();
    }
    TagLib::File *file = ref.file();
    for (const auto &[key, values] : file->properties()) {
        int i = 0;
        for (const auto &v : values) w.add("P", key.to8Bit(true), i++, v);
    }
    w.add("A", "codec", 0, codecName(file));
    if (const auto *p = file->audioProperties()) {
        w.add("A", "lengthMs", 0, std::to_string(p->lengthInMilliseconds()));
        w.add("A", "bitrate", 0, std::to_string(p->bitrate()));
        w.add("A", "sampleRate", 0, std::to_string(p->sampleRate()));
        w.add("A", "channels", 0, std::to_string(p->channels()));
        w.add("A", "bits", 0, std::to_string(bitsPerSample(p)));
    }
    bool hasPicture = false;
    for (const auto &k : file->complexPropertyKeys()) {
        if (k == "PICTURE" && !file->complexProperties(k).isEmpty()) hasPicture = true;
    }
    w.add("A", "picture", 0, std::string(hasPicture ? "1" : "0"));
    return w.bytes();
}

/** 埋め込み画像のうち表紙（Front Cover）を優先して1枚返す。なければ空。 */
std::vector<uint8_t> readPicture(const std::string &path) {
    TagLib::FileRef ref(path.c_str(), false);
    if (ref.isNull() || !ref.file()) return {};
    const auto pictures = ref.file()->complexProperties("PICTURE");
    const TagLib::VariantMap *chosen = nullptr;
    for (const auto &pic : pictures) {
        if (!chosen) chosen = &pic;
        if (pic.value("pictureType").toString() == "Front Cover") {
            chosen = &pic;
            break;
        }
    }
    if (!chosen) return {};
    const TagLib::ByteVector data = chosen->value("data").toByteVector();
    return std::vector<uint8_t>(data.begin(), data.end());
}

std::string toPath(JNIEnv *env, jbyteArray pathUtf8) {
    const jsize len = env->GetArrayLength(pathUtf8);
    std::string path(static_cast<size_t>(len), '\0');
    env->GetByteArrayRegion(pathUtf8, 0, len, reinterpret_cast<jbyte *>(path.data()));
    return path;
}

jbyteArray toJava(JNIEnv *env, const std::vector<uint8_t> &out) {
    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    if (!result) return nullptr;
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()), reinterpret_cast<const jbyte *>(out.data()));
    return result;
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_oke1943_azsaplayer_tag_TagLibNative_dumpNative(JNIEnv *env, jobject /* this */, jbyteArray pathUtf8) {
    return toJava(env, dumpFile(toPath(env, pathUtf8)));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_oke1943_azsaplayer_tag_TagLibNative_readNative(JNIEnv *env, jobject /* this */, jbyteArray pathUtf8) {
    return toJava(env, readFile(toPath(env, pathUtf8)));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_oke1943_azsaplayer_tag_TagLibNative_pictureNative(JNIEnv *env, jobject /* this */, jbyteArray pathUtf8) {
    const auto bytes = readPicture(toPath(env, pathUtf8));
    if (bytes.empty()) return nullptr;
    return toJava(env, bytes);
}
